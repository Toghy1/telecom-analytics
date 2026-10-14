#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
模拟用户访问脚本 —— 造 500 个模拟用户，并为每人生成 100 条操作记录，
再按《开发文档》§5.4 注入合计 8% 的脏数据。

产出（默认写到 mock/out/）：

    users.json                 500 个用户：id / 手机号 / 归属地 / 套餐
    behavior_YYYY-MM-DD.jsonl  按天切分的行为日志，每人 100 条基础记录

字段严格对齐《开发文档》§5.1.2 的 10 个公共字段，12 个 event_code 取自 §5.1.1，
与 frontend/src/utils/tracker.js 上报的是同一套 —— 所以造出来的日志能直接喂大屏，
不需要在 Spark 里做字段名转换。

四处取值不是随便写的，写错了不报错、只是悄悄错：

· region 用**省级名**，逐字等于 frontend/public/map/china.json 的 properties.name。
  大屏地域图按这个字符串匹配省份，截字符串推省名会静默少一块（各省名字长度不一）。
· event_time 是 **真 UTC + 毫秒** 的 ISO8601，与前端 `new Date().toISOString()` 同形。
  脚本内部一律按北京时间想事情（「晚上 8 点最活跃」），落盘时才转 UTC ——
  两个时区在这里错位一次，换算全交给后端/Spark。
  日志文件按**北京日期**切分（behavior_2026-09-27.jsonl 装的是那天北京时间的记录），
  不是按 event_time 那串 UTC 字面量切 —— 两者差 8 小时，早班那批会跑到前一天去。
· 脏数据**只落在非漏斗事件上**：漏斗四段（view_home → login → query_* →
  order_package）一条都不能改脏。把某会话的 login 改脏，那个会话就等于没登录过，
  大屏漏斗会出现「查询 > 登录」这种往上翘的一级。
· 重复上报是**逐字复制**一行，连 event_time 一起复制 —— 明细表唯一索引是
  uk_event(event_id, event_date)，跨天复制不撞索引，等于没造出「重复」。

只依赖 Python 标准库，不需要 pip 装任何东西。
"""

import argparse
import json
import os
import random
import sys
import uuid
from datetime import datetime, timedelta, timezone

# ===========================================================================
# 一、常量（口径全部来自《开发文档》§5.1 / §5.4 与前端源码）
# ===========================================================================

#: 12 个埋点事件 → 页面路径。路径取自 frontend/src/router/index.js。
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

#: 行为漏斗四段。这几类事件**不参与脏数据注入**，理由见文件头。
FUNNEL_EVENTS = frozenset((
    'view_home', 'login',
    'query_balance', 'query_bill', 'query_usage',
    'order_package',
))

#: 单个会话里的动作池与权重。login / logout 不在池里 —— 它们是会话的
#: 头尾，由 session_events() 单独加，混进池子会出现「登录了两次」。
#: ⚠️ 三个「游客页」也在池子里：登录用户回首页/翻套餐是正常行为，
#:    前端顶栏就一直挂着这两个入口。不含它们的话行为漏斗的第一段永远是 0。
ACTION_WEIGHTS = (
    ('view_dashboard', 18),
    ('query_balance', 16),
    ('query_bill', 14),
    ('query_usage', 14),
    ('view_profile', 10),
    ('view_package_list', 8),
    ('view_home', 6),
    ('view_promo', 6),
    ('order_package', 5),
    ('submit_ticket', 3),
)

#: 设备分布（前端 detectDevice 的三个取值）。PC 偏多，符合「自助营业厅」场景。
DEVICES = (('PC', 55), ('MOBILE', 40), ('OTHER', 5))

#: 24 小时活跃权重 —— 早高峰 + 晚高峰双峰，凌晨近乎为 0。
#: 大屏有「24 小时时段热力图」，权重平铺的话那张图就是一条直线，看不出东西。
HOUR_WEIGHTS = (
    1, 1, 1, 1, 1, 1,          # 00—05 凌晨
    2, 5, 9, 12, 14, 12,       # 06—11 上午（09:00 起峰）
    8, 8, 9, 10, 11, 12,       # 12—17 下午
    15, 18, 17, 12, 6, 2,      # 18—23 晚间（20:00 峰）
)

#: 归属地池 —— 取大陆 31 个省级行政区，**逐字**等于 china.json 的 properties.name。
PROVINCES = (
    '北京市', '天津市', '河北省', '山西省', '内蒙古自治区', '辽宁省', '吉林省',
    '黑龙江省', '上海市', '江苏省', '浙江省', '安徽省', '福建省', '江西省',
    '山东省', '河南省', '湖北省', '湖南省', '广东省', '广西壮族自治区', '海南省',
    '重庆市', '四川省', '贵州省', '云南省', '西藏自治区', '陕西省', '甘肃省',
    '青海省', '宁夏回族自治区', '新疆维吾尔自治区',
)

#: 在售套餐（id / 名称），取自 frontend/src/api/mock/package.js。
#: ⚠️ id=5「学生特惠」是 status=0（停售），不在这里 —— 停售套餐不该有人在用。
PACKAGES = (
    (1, '畅享 5G 39 元套餐'),
    (2, '畅享 5G 79 元套餐'),
    (3, '畅享 5G 129 元套餐'),
    (4, '孝心卡 19 元套餐'),
)

#: 套餐分布权重 —— 39 元是主推款。
PACKAGE_WEIGHTS = (1, 34), (2, 30), (3, 18), (4, 18)

#: 号段池（中国电信号段为主）
MOBILE_PREFIX = ('133', '153', '173', '177', '180', '181', '189', '199')

#: 归属地池权重 —— 一线与新一线偏多，避免大屏地图 31 个省全部均等一片绿。
PROVINCE_WEIGHTS = (
    9, 3, 5, 3, 2, 4, 2, 3, 9, 8, 8, 4, 5, 4, 7, 6, 6, 5, 9,
    3, 1, 4, 7, 2, 3, 1, 3, 2, 1, 2, 2,
)

#: 一个会话里最多几个动作（不含 login / logout）
SESSION_ACTIONS = (1, 7)

#: 会话以「正常退出」收尾的概率；否则就是直接关掉（没有 logout 事件）
LOGOUT_RATIO = 0.55

# --- 脏数据 -------------------------------------------------------------

#: 判脏规则与注入比例（**占入库总行数**）——《开发文档》§5.4。
#: 比例在这里可调（`--dirty` 整体等比缩放），不做界面。
DIRTY_RULES = (
    ('MISSING_FIELD', 0.020),         # 1 必填字段缺失
    ('ENUM_OUT_OF_RANGE', 0.015),     # 2 枚举越界
    ('TYPE_ERROR', 0.010),            # 3 字段类型错误
    ('TIME_OUT_OF_RANGE', 0.010),     # 4 时间戳非法或越界
    ('BAD_IDENTIFIER', 0.010),        # 5 标识格式非法
    ('DUPLICATE', 0.010),             # 6 重复上报
    ('TOO_LONG_OR_MOJIBAKE', 0.005),  # 7 字段超长 / 乱码
)

#: 规则 1—7 的原始合计（0.08）。`--dirty` 按它做等比缩放。
DIRTY_TOTAL_RATIO = sum(r for _, r in DIRTY_RULES)

#: 规则 1 里的「必填字段」，取自 §5.1.2
REQUIRED_FIELDS = ('event_id', 'event_code', 'event_time',
                   'session_id', 'page', 'region', 'device')

#: 规则 4 的「早于系统上线日」判据
SYSTEM_ONLINE_DATE = datetime(2024, 1, 1, tzinfo=timezone.utc)

#: 规则 2 用来越界的值 —— 都不在 §5.1.1 / §5.1.2 的枚举里
BOGUS_EVENT_CODES = ('view_unknown', 'click_banner', 'share_page', 'search_package')
BOGUS_DEVICES = ('TABLET', 'TV', 'WATCH')

#: 中国标准时区。脚本里的「几点」一律按北京时间理解，落盘前转成 UTC。
CHINA_TZ = timezone(timedelta(hours=8))


def iso(dt):
    """
    真 UTC 毫秒精度 ISO8601，与前端 `new Date().toISOString()` 同形。

    ⚠️ 必须先 astimezone，不能拿本地时间直接贴个 `Z` 就算完 —— 那样发出去的
    是「本地钟点伪装成 UTC」，差 8 小时。后端按 +08:00 换算回来时，晚高峰
    会整体挪到凌晨，而且**不报任何错**。写入的 dt 必须是带时区的。
    """
    u = dt.astimezone(timezone.utc)
    return u.strftime('%Y-%m-%dT%H:%M:%S.') + '%03dZ' % (u.microsecond // 1000)


def pick(rng, pairs):
    """按权重取一个值。pairs 形如 ((值, 权重), (值, 权重), ...)。"""
    values = [p[0] for p in pairs]
    weights = [p[1] for p in pairs]
    return rng.choices(values, weights=weights, k=1)[0]


def rand_ip(rng):
    """随便一个合法的公网 IPv4。前端那个字段是 null，这里填上让数据更实。"""
    return '%d.%d.%d.%d' % (
        rng.randint(1, 223), rng.randint(0, 255),
        rng.randint(0, 255), rng.randint(1, 254),
    )


# ===========================================================================
# 二、造 500 个用户
# ===========================================================================

def build_users(rng, total, start_id=1001):
    """造 users.json 的那份名单。手机号不重复 —— 它是登录名，是唯一键。"""
    used = set()
    users = []
    for i in range(total):
        while True:
            phone = rng.choice(MOBILE_PREFIX) + '%08d' % rng.randint(0, 99999999)
            if phone not in used:
                used.add(phone)
                break
        pkg_id = pick(rng, PACKAGE_WEIGHTS)
        users.append({
            'user_id': start_id + i,
            'login_name': phone,
            'region': rng.choices(PROVINCES, weights=PROVINCE_WEIGHTS, k=1)[0],
            'package_id': pkg_id,
            'package_name': dict(PACKAGES)[pkg_id],
            # 每个用户固定用一个设备 —— 同一个人一会儿 PC 一会儿手机，
            # 大屏按设备分层时会把同一人拆成两拨
            'device': pick(rng, DEVICES),
        })
    return users


# ===========================================================================
# 三、造行为日志
# ===========================================================================

def session_events(rng, user, session_id, start_dt, ticket_counter):
    """
    生成一个会话：login → 若干动作 → (可能) logout。

    动作按权重随机挑，可能重复（同一个人连查两次话费很正常）。
    返回按时间递增的事件列表。
    """
    codes = ['login']
    for _ in range(rng.randint(*SESSION_ACTIONS)):
        codes.append(pick(rng, ACTION_WEIGHTS))
    if rng.random() < LOGOUT_RATIO:
        codes.append('logout')

    events = []
    t = start_dt
    for code in codes:
        ext = None
        if code == 'order_package':
            ext = {'package_id': pick(rng, PACKAGE_WEIGHTS)}
        elif code == 'submit_ticket':
            ticket_counter[0] += 1
            ext = {'ticket_id': 90000 + ticket_counter[0]}

        events.append({
            'event_id': str(uuid.UUID(int=rng.getrandbits(128))),
            'event_code': code,
            'event_time': iso(t),
            'user_id': user['user_id'],
            'session_id': session_id,
            'page': EVENT_PAGE[code],
            'region': user['region'],
            'device': user['device'],
            'ip': user['ip'],
            'ext': ext,
            # 只是给 main() 按天归堆用的，落盘前 pop 掉，不进最终 JSON
            '_day': t.date().isoformat(),
        })
        # 一个动作到下一个动作隔几秒到几分钟
        t += timedelta(seconds=rng.randint(5, 240))
    return events


def build_events_for_user(rng, user, total, days, end_day, ticket_counter):
    """
    给一个用户造 total 条记录。

    做法：反复开新会话直到凑够 total 条，最后一批多余的直接截断。
    截断只会砍掉末尾那一段，前面的会话结构仍是完整的（login 打头）。
    """
    events = []
    while len(events) < total:
        day = end_day - timedelta(days=rng.randrange(days))
        hour = rng.choices(range(24), weights=HOUR_WEIGHTS, k=1)[0]
        start = datetime(day.year, day.month, day.day, hour,
                         rng.randint(0, 59), rng.randint(0, 59),
                         rng.randint(0, 999) * 1000, tzinfo=CHINA_TZ)
        sid = str(uuid.UUID(int=rng.getrandbits(128)))
        events.extend(session_events(rng, user, sid, start, ticket_counter))
    events = events[:total]
    # 按时间排一遍 —— 会话是按随机的天/点开的，不排的话日志里时间乱跳
    events.sort(key=lambda e: e['event_time'])
    return events


# ===========================================================================
# 四、注入脏数据（《开发文档》§5.4）
# ===========================================================================

def plan_dirty(base_rows, dirty_ratio):
    """
    算出「注入多少条才让脏行恰好占入库总行数的 dirty_ratio」。

    设 T = 入库总行数、d = 重复行数、m = 被改脏的行数：
        T = base_rows + d          （重复行是**新增**的；其余 6 条是就地改脏）
        m + d = dirty_ratio × T    （脏行合计占总量）
        d = dup_ratio × T
    ⇒   T = base_rows / (1 - dup_ratio)

    ⚠️ 不能拿 `base_rows × 8%` 当脏行数 —— 那是把分母取成了干净行数，
       跟 §5.4 的「占总量」对不上，跑完对着文档一核账就发现比例偏低。
    """
    dup_ratio = dict(DIRTY_RULES)['DUPLICATE'] * (dirty_ratio / DIRTY_TOTAL_RATIO)
    total = base_rows / (1.0 - dup_ratio)
    scale = dirty_ratio / DIRTY_TOTAL_RATIO
    return total, {name: int(round(ratio * scale * total))
                   for name, ratio in DIRTY_RULES}


def foul(rng, row, rule, now):
    """就地改脏一行。每种规则的具体表现见《开发文档》§5.4 的判脏规则表。"""
    if rule == 'MISSING_FIELD':
        row[rng.choice(REQUIRED_FIELDS)] = None

    elif rule == 'ENUM_OUT_OF_RANGE':
        if rng.random() < 0.6:
            row['event_code'] = rng.choice(BOGUS_EVENT_CODES)
        else:
            row['device'] = rng.choice(BOGUS_DEVICES)

    elif rule == 'TYPE_ERROR':
        if rng.random() < 0.5:
            row['user_id'] = str(row['user_id'])          # bigint 传成字符串
        else:
            row['event_time'] = '2026-09-26 14:50:04'     # 非 ISO8601

    elif rule == 'TIME_OUT_OF_RANGE':
        if rng.random() < 0.5:
            row['event_time'] = iso(now + timedelta(days=rng.randint(1, 30)))
        else:
            row['event_time'] = iso(
                SYSTEM_ONLINE_DATE - timedelta(days=rng.randint(1, 400)))

    elif rule == 'BAD_IDENTIFIER':
        roll = rng.random()
        if roll < 0.4:
            row['event_id'] = 'evt-%06d' % rng.randint(0, 999999)
        elif roll < 0.8:
            row['session_id'] = 'sess_%06d' % rng.randint(0, 999999)
        else:
            row['ip'] = '%d.%d.%d.%d' % (rng.randint(256, 999), rng.randint(0, 999),
                                         rng.randint(0, 999), rng.randint(0, 999))

    elif rule == 'TOO_LONG_OR_MOJIBAKE':
        if rng.random() < 0.5:
            row['page'] = '/user/' + 'x' * 70              # 超过 64 字符
        else:
            # 控制字符 + 替换字符。**刻意用合法的 JSON 转义**（\u0000 等），
            # 让文件本身仍能被正常解析 —— 真写成非法 UTF-8 字节的话，
            # 这份 jsonl 连编辑器都打不开，演示时反而添乱。
            row['page'] = '/user/\u0000\u0007��'


def inject_dirty(rng, rows, dirty_ratio):
    """
    就地注入脏数据，并把重复行追加进 rows。返回 (各规则命中数, 重复行数)。

    改脏只在**非漏斗事件**里挑，重复行也只在**没被改脏**的行里挑 ——
    复制一行已经被 null 掉 event_id 的行，造出来的「重复」名不副实。
    """
    hits = {name: 0 for name, _ in DIRTY_RULES}
    if dirty_ratio <= 0:
        return hits, 0

    _, counts = plan_dirty(len(rows), dirty_ratio)
    eligible = [r for r in rows if r['event_code'] not in FUNNEL_EVENTS]
    shuffled = rng.sample(eligible, len(eligible))

    mutate_rules = [name for name, _ in DIRTY_RULES if name != 'DUPLICATE']
    n_mutate = min(sum(counts[n] for n in mutate_rules), len(shuffled))
    to_mutate, clean_rest = shuffled[:n_mutate], shuffled[n_mutate:]

    now = datetime.now(timezone.utc)
    cursor = 0
    for name in mutate_rules:
        take = to_mutate[cursor:cursor + counts[name]]
        for row in take:
            foul(rng, row, name, now)
        hits[name] = len(take)
        cursor += len(take)

    n_dup = min(counts['DUPLICATE'], len(clean_rest))
    for row in rng.sample(clean_rest, n_dup):
        rows.append(dict(row))    # 逐字复制，连 event_time 和 _day 一起
    hits['DUPLICATE'] = n_dup
    return hits, n_dup


# ===========================================================================
# 五、入口
# ===========================================================================

def main():
    # Windows 控制台默认 GBK，直接 print 中文会乱码、遇到符号还会崩。
    # 产出文件始终是 UTF-8，这里只管控制台这一路。
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except (AttributeError, OSError):
        pass

    ap = argparse.ArgumentParser(
        description='模拟用户访问脚本 —— 造模拟用户 + 行为日志（操作记录 + 脏数据）')
    ap.add_argument('--users', type=int, default=500, help='模拟用户数（默认 500）')
    ap.add_argument('--events-per-user', type=int, default=100,
                    help='每个用户的操作记录条数（默认 100）')
    ap.add_argument('--days', type=int, default=14,
                    help='回溯天数（默认 14；7 日留存至少要 14 天）')
    ap.add_argument('--end', default=None,
                    help='结束日期 YYYY-MM-DD，默认昨天（今天归实时链路写）')
    ap.add_argument('--out', default='out', help='输出目录（默认 out）')
    ap.add_argument('--seed', type=int, default=20261010, help='随机种子，同种子可复现')
    ap.add_argument('--dirty', type=float, default=0.08,
                    help='脏数据占比（占入库总行数，默认 0.08；给 0 则不注入）')
    args = ap.parse_args()

    if args.events_per_user < 1:
        ap.error('--events-per-user 至少为 1')
    if args.days < 1:
        ap.error('--days 至少为 1')
    if not 0 <= args.dirty < 1:
        ap.error('--dirty 取值范围 [0, 1)')

    rng = random.Random(args.seed)
    end_day = (datetime.strptime(args.end, '%Y-%m-%d').date() if args.end
               else (datetime.now() - timedelta(days=1)).date())

    os.makedirs(args.out, exist_ok=True)

    users = build_users(rng, args.users)
    for u in users:
        u['ip'] = rand_ip(rng)

    # --- 逐个用户造日志，先攒成一个平铺的列表 ---
    rows = []
    ticket_counter = [0]
    for u in users:
        rows.extend(build_events_for_user(
            rng, u, args.events_per_user, args.days, end_day, ticket_counter))

    base_rows = len(rows)
    per_user = {}
    for ev in rows:
        per_user[ev['user_id']] = per_user.get(ev['user_id'], 0) + 1

    # --- 注入脏数据 ---
    hits, n_dup = inject_dirty(rng, rows, args.dirty)
    total_rows = len(rows)

    # --- 落盘：按**北京日期**归堆，不是按 event_time 那串 UTC 字面量 ---
    # 否则北京时间 09-27 早上 7 点那批会被分进 09-26 的文件里。
    by_day = {}
    for ev in rows:
        by_day.setdefault(ev.pop('_day'), []).append(ev)

    users_path = os.path.join(args.out, 'users.json')
    with open(users_path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(users, f, ensure_ascii=False, indent=2)
        f.write('\n')

    written = []
    for day in sorted(by_day):
        path = os.path.join(args.out, 'behavior_%s.jsonl' % day)
        with open(path, 'w', encoding='utf-8', newline='\n') as f:
            for ev in by_day[day]:
                f.write(json.dumps(ev, ensure_ascii=False) + '\n')
        written.append((day, len(by_day[day]), path))

    # --- 简报 ---
    print('[用户] %d 个 → %s' % (args.users, users_path))
    print('[日志] 每人 %d 条 × %d 人 = %d 条基础记录'
          % (args.events_per_user, args.users, base_rows))
    bad = [uid for uid, n in per_user.items() if n != args.events_per_user]
    print('[自检] 注入前每人条数 %s'
          % ('全部等于 %d ✅' % args.events_per_user if not bad
             else '有 %d 个用户不是 %d 条 ❌' % (len(bad), args.events_per_user)))

    if args.dirty > 0:
        n_mut = sum(v for k, v in hits.items() if k != 'DUPLICATE')
        print('[脏数据] 干净 %d 条 + 脏 %d 条（%d 条改脏 + %d 条重复）'
              '，占入库 %d 行的 %.2f%%'
              % (base_rows - n_mut, n_mut + n_dup, n_mut, n_dup,
                 total_rows, 100.0 * (n_mut + n_dup) / total_rows))
        for name, ratio in DIRTY_RULES:
            print('         %-22s %5d 条   应为 %.1f%%'
                  % (name, hits[name], ratio * 100))
    else:
        print('[脏数据] 未注入（--dirty 0），入库 %d 行' % total_rows)

    print('[日志] 日期 %s ~ %s，共 %d 个文件 → %s'
          % (written[0][0], written[-1][0], len(written), args.out))

    dist = {}
    for evs in by_day.values():
        for ev in evs:
            dist[ev['event_code']] = dist.get(ev['event_code'], 0) + 1
    print('[分布] ' + '  '.join(
        '%s=%d' % (c, dist.get(c, 0)) for c in EVENT_PAGE if c in dist))


if __name__ == '__main__':
    main()
