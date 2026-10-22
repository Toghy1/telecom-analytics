#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
实时埋点长跑脚本 —— 按固定间隔往 `POST /api/track` 发干净事件，
让大屏的实时板块（五个数字卡 / 趋势 / 页面热度 / 事件计数）一直有数据在动。

设计取舍：**无状态随机 + 会话内复用**
------------------------------------------------
不做会话状态机（不模拟「首页 → 套餐 → 登录 → 下单」的顺序），每事件独立随机；
唯一的「状态」是 session_id：一个会话连发 3—5 条，发完再开新会话。

为什么保留这一点点状态：大屏「当前在线用户数」的口径是**近 5 分钟内有行为的
会话数**。若每条都换 session_id，在线数会变成「发送速率 × 300 秒」并一路上涨，
看着明显是假的；按会话复用之后，在线数约等于「同时活着的会话数」，有起伏、不失控。
页面热度仍是随机分布（这是「不做状态机」的代价，接受）。

字段口径与 `simulate_access.py` / `frontend/src/utils/tracker.js` 完全一致：
必填 7 个（event_id / event_code / event_time / session_id / page / region / device），
event_id 与 session_id 必须是 UUID，event_time 是真 UTC + 毫秒的 ISO8601。
任一条不满足，事件不会上大屏，而是进死信表 `dwd_behavior_dirty`。

用法（只依赖 Python 标准库，不需要 pip 装东西）：

    python mock/realtime_feed.py                      # 每 2 秒一条，一直发
    python mock/realtime_feed.py --interval 5         # 每 5 秒一条
    python mock/realtime_feed.py --count 20           # 只发 20 条，发完退出
    python mock/realtime_feed.py --dry-run --count 3  # 只打印报文，不发送
    python mock/realtime_feed.py --url http://127.0.0.1:8082/api/track   # 本地联调

⚠️ `POST /api/track` 返回 200 不代表入库成功 —— 埋点是旁路，Kafka 挂了它也回 200。
   验证要看 `/api/realtime/metrics` 的数值，或查库：
   `SELECT COUNT(*) FROM dwd_behavior_log`。
"""

import argparse
import json
import random
import sys
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timezone

# 12 个 event_code → 页面路径，取自 frontend/src/router/index.js
# （与 BehaviorValidator.EVENT_CODES、simulate_access.py 的 EVENT_PAGE 同源）。
EVENT_PAGE = {
    'view_home': '/',
    'view_package_list': '/packages',
    'view_promo': '/promos',
    'login': '/login',
    'logout': '/login',
    'view_dashboard': '/user/dashboard',
    'query_balance': '/user/balance',
    'query_bill': '/user/bills',
    'query_usage': '/user/usage',
    'order_package': '/user/order',
    'submit_ticket': '/user/ticket',
    'view_profile': '/user/profile',
}

# 事件权重：浏览类偏多，办理 / 报障偏少。纯均匀随机也能跑，
# 只是页面热度图会「十二条一样高」，一眼看出是造的。
EVENT_WEIGHTS = {
    'view_home': 30,
    'view_package_list': 15,
    'view_promo': 10,
    'view_dashboard': 8,
    'query_balance': 7,
    'query_bill': 7,
    'query_usage': 7,
    'view_profile': 6,
    'order_package': 6,
    'submit_ticket': 4,
    'login': 0,    # 游客占多数，登录 / 退出由 --user-ratio 里的用户会话带出
    'logout': 0,
}

#: region 用省级名，逐字等于 frontend/public/map/china.json 的 properties.name。
PROVINCES = (
    '北京市', '天津市', '河北省', '山西省', '内蒙古自治区', '辽宁省', '吉林省',
    '黑龙江省', '上海市', '江苏省', '浙江省', '安徽省', '福建省', '江西省',
    '山东省', '河南省', '湖北省', '湖南省', '广东省', '广西壮族自治区', '海南省',
    '重庆市', '四川省', '贵州省', '云南省', '西藏自治区', '陕西省', '甘肃省',
    '青海省', '宁夏回族自治区', '新疆维吾尔自治区',
)

#: 省份权重：演示主场景是上海，其次几个经济大省。地域图靠这个出高低。
PROVINCE_WEIGHTS = (
    6, 2, 3, 2, 1, 2, 2,
    2, 30, 6, 5, 3, 4, 3,
    15, 4, 5, 4, 20, 2, 2,
    3, 4, 2, 1, 2, 2, 2,
    1, 1, 1,
)

DEVICES = ('PC', 'MOBILE', 'MOBILE', 'OTHER')  # 手机端偏多

#: 业务账号 id 区间（t_account 的种子 id 到 1500 为止）。
USER_ID_RANGE = (1000, 1500)


def iso_now(offset_seconds):
    """当前 UTC 时间减去 offset 秒，输出与 `new Date().toISOString()` 同形的字符串。

    ⚠️ 必须带毫秒和 Z：后端判时间格式用的是
    `^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z$`，差一点都算 TYPE_ERROR。
    """
    dt = datetime.fromtimestamp(time.time() - offset_seconds, tz=timezone.utc)
    return dt.strftime('%Y-%m-%dT%H:%M:%S.') + '%03dZ' % (dt.microsecond // 1000)


class Session(object):
    """一个会话：固定的 session_id / region / device / user_id，连发 3—5 条后退休。"""

    def __init__(self, rng, user_ratio):
        self.session_id = str(uuid.uuid4())
        self.region = rng.choices(PROVINCES, weights=PROVINCE_WEIGHTS, k=1)[0]
        self.device = rng.choice(DEVICES)
        self.ip = '10.%d.%d.%d' % (rng.randint(0, 255), rng.randint(0, 255),
                                   rng.randint(1, 254))
        # 一个会话要么是游客（user_id 为空），要么属于某个已登录用户
        self.user_id = (rng.randint(*USER_ID_RANGE)
                        if rng.random() < user_ratio else None)
        self.left = rng.randint(3, 5)
        self.first = True   # 第一条约等于「进入页」，第二条起才是动作


def pick_event(rng, session, first):
    """挑一个 event_code。会话第一条偏向落地页，之后按权重随机。"""
    if first:
        if session.user_id is None:
            return rng.choice(['view_home', 'view_package_list', 'view_promo'])
        return rng.choices(['login', 'view_home', 'view_dashboard'],
                           weights=(60, 25, 15), k=1)[0]
    codes = list(EVENT_WEIGHTS.keys())
    weights = list(EVENT_WEIGHTS.values())
    code = rng.choices(codes, weights=weights, k=1)[0]
    # 登录用户偶尔补一条 login / logout，让事件计数里这两类不总是 0
    if session.user_id is not None and rng.random() < 0.08:
        return rng.choice(['login', 'logout'])
    return code


def build_event(rng, session, code, skew):
    """拼一条事件。字段与 simulate_access.py 的公共 10 字段一致。"""
    return {
        'event_id': str(uuid.uuid4()),
        'event_code': code,
        'event_time': iso_now(rng.uniform(skew, skew + 3)),
        'session_id': session.session_id,
        'page': EVENT_PAGE[code],
        'region': session.region,
        'device': session.device,
        'user_id': session.user_id,
        'ip': session.ip,
        'ext': {'feed': 'realtime_feed'},
    }


def post(url, payload, timeout):
    """POST 一条 JSON。返回 (是否发出, 说明)。

    ⚠️ 返回 200 只说明「接口收下了」，不代表入库 —— 埋点投 Kafka 失败也回 200。
    """
    body = json.dumps(payload, ensure_ascii=False).encode('utf-8')
    req = urllib.request.Request(url, data=body, method='POST', headers={
        'Content-Type': 'application/json; charset=utf-8',
    })
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            text = resp.read().decode('utf-8', 'replace')
            return True, 'HTTP %d %s' % (resp.status, text[:80])
    except urllib.error.HTTPError as e:
        return False, 'HTTP %d %s' % (e.code, e.read().decode('utf-8', 'replace')[:80])
    except Exception as e:                                  # noqa: BLE001
        return False, '%s: %s' % (type(e).__name__, e)


def main():
    ap = argparse.ArgumentParser(
        description='按间隔往埋点接口发干净事件，让实时大屏一直有数据（无状态随机 + 会话复用）')
    ap.add_argument('--url', default='http://192.168.23.133/api/track',
                    help='埋点接口地址（默认走 .133 的 nginx）')
    ap.add_argument('--interval', type=float, default=2.0,
                    help='两条之间的间隔秒数（默认 2.0）')
    ap.add_argument('--count', type=int, default=0,
                    help='总共发多少条，0 表示一直发（默认 0）')
    ap.add_argument('--user-ratio', type=float, default=0.35,
                    help='会话属于已登录用户的比例（默认 0.35，其余为游客）')
    ap.add_argument('--skew', type=float, default=10.0,
                    help='event_time 相对本机时间往前推的秒数（默认 10；'
                         '各机器时钟不同步，留余量可避免被判 TIME_ILLEGAL）')
    ap.add_argument('--timeout', type=float, default=5.0, help='单条请求超时秒数')
    ap.add_argument('--seed', type=int, default=None, help='随机种子，同种子可复现')
    ap.add_argument('--dry-run', action='store_true', help='只打印报文，不发请求')
    args = ap.parse_args()

    # Windows 控制台默认 GBK，打印中文 region 会乱码；能切就切到 UTF-8
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8', errors='replace')

    if args.interval <= 0:
        ap.error('--interval 必须大于 0')
    if not 0 <= args.user_ratio <= 1:
        ap.error('--user-ratio 必须在 [0, 1] 之间')

    rng = random.Random(args.seed)
    session = None
    sent = failed = 0
    started = time.time()

    print('target : %s' % args.url)
    print('rate   : %.2fs/event, count=%s, skew=%.0fs, dry-run=%s'
          % (args.interval, args.count or 'unlimited', args.skew, args.dry_run))
    print('-' * 68)

    try:
        while args.count == 0 or sent + failed < args.count:
            if session is None or session.left <= 0:
                session = Session(rng, args.user_ratio)
            first = session.first
            session.first = False
            session.left -= 1

            code = pick_event(rng, session, first)
            payload = build_event(rng, session, code, args.skew)

            if args.dry_run:
                print(json.dumps(payload, ensure_ascii=False, sort_keys=True))
                ok, note = True, 'dry-run'
            else:
                ok, note = post(args.url, payload, args.timeout)

            # 控制台一律只打 ASCII：Windows 控制台默认 GBK，中文会抛 UnicodeEncodeError
            tag = 'OK ' if ok else 'ERR'
            sid = session.session_id[:8]
            print('[%s] #%d %s %-18s session=%s n=%d %s'
                  % (time.strftime('%H:%M:%S'), sent + failed + 1, tag,
                     code, sid, session.left, '' if ok else note))

            if ok:
                sent += 1
            else:
                failed += 1
            if args.count == 0 or sent + failed < args.count:
                time.sleep(args.interval)
    except KeyboardInterrupt:
        print('\ninterrupted')

    elapsed = time.time() - started
    print('-' * 68)
    print('sent=%d failed=%d elapsed=%.1fs' % (sent, failed, elapsed))
    if not args.dry_run and sent:
        print('note: HTTP 200 only means accepted; check /api/realtime/metrics '
              'or SELECT COUNT(*) FROM dwd_behavior_log')
    return 0 if failed == 0 else 1


if __name__ == '__main__':
    sys.exit(main())
