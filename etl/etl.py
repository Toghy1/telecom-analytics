#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
离线 ETL：mock/out 的行为日志 → MySQL demo01。

这是「开发期本地 Windows 最小闭环」里的**离线批处理替身**（决策
「开发期采用本地 Windows 最小闭环」）。正式链路在 VM 上由 Spark 作业完成，
本脚本只负责把「数据 → 接口」这一段先跑通：

    mock/out/users.json          → t_package / t_account
    mock/out/behavior_*.jsonl    → 校验 → dwd_behavior_log
                                     └──→ dwd_behavior_dirty
    明细 + 用户表                → dws_metric_offline

口径与《开发文档》§5.4 的七条判脏规则一致；规则编码在入库前改写为前端
`DIRTY_RULE` 的枚举（决策「脏数据清洗中的正则只做格式校验」）。

⚠️ 本脚本面向本地演示库：每次运行会清空行为三表与业务种子两表
   （t_account / t_package），请勿指向任何有真实数据的库。

用法：
    python etl/etl.py --password <MySQL root 口令>
    python etl/etl.py --password ... --input mock/out --database demo01

依赖：pymysql（仅此一个第三方包）。
"""

import argparse
import json
import os
import re
import sys
from collections import Counter, defaultdict
from datetime import datetime, timedelta, timezone

import pymysql

# ===========================================================================
# 一、常量与口径
# ===========================================================================

#: 12 个埋点事件（§5.1.1）
EVENT_CODES = {
    'view_home', 'view_package_list', 'view_promo',
    'login', 'logout',
    'view_dashboard', 'query_balance', 'query_bill', 'query_usage',
    'order_package', 'submit_ticket', 'view_profile',
}

#: 规则 1 的必填字段（§5.1.2）
REQUIRED_FIELDS = ('event_id', 'event_code', 'event_time',
                   'session_id', 'page', 'region', 'device')

DEVICES = {'PC', 'MOBILE', 'OTHER'}

#: 漏斗第三段「查询」包含的三个事件
QUERY_EVENTS = {'query_balance', 'query_bill', 'query_usage'}

#: 系统上线日。早于它的时间视为「越界」（规则 4）
SYSTEM_ONLINE_DATE = datetime(2024, 1, 1, tzinfo=timezone.utc)

#: 北京时区。event_date 按它换算，不能直接取 UTC 字面量的日期。
CHINA_TZ = timezone(timedelta(hours=8))

#: 与前端 `toISOString()` 同形：UTC + 毫秒
TIME_RE = re.compile(r'^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$')

#: 宽松 UUID：36 位、8-4-4-4-12、十六进制。
#: **刻意不校验 RFC 版本位 / 变体位**——造数脚本用 uuid.UUID(int=random)，
#: 不设这两组位，严格式会把全部正常行判成脏。见决策「正则只做格式校验」。
UUID_RE = re.compile(
    r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-'
    r'[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$')
IPV4_RE = re.compile(r'^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$')

#: 脚本内部规则名 → 前端 `DIRTY_RULE` 的枚举值。
#: 对不上号会让子页「七条规则命中分布」出现空条与不存在的条。
RULE_CODE = {
    'MISSING_FIELD': 'MISSING_REQUIRED',
    'ENUM_OUT_OF_RANGE': 'ENUM_OUT_OF_RANGE',
    'TYPE_ERROR': 'TYPE_ERROR',
    'TIME_OUT_OF_RANGE': 'TIME_ILLEGAL',
    'BAD_IDENTIFIER': 'ID_FORMAT_ERROR',
    'DUPLICATE': 'DUPLICATE',
    'TOO_LONG_OR_MOJIBAKE': 'OVERLONG_GARBLED',
}

#: 活跃度分层（近 30 天事件数）
ACTIVITY_HIGH = 50
ACTIVITY_MID = 10
ACTIVITY_LABELS = ('高频活跃', '中频活跃', '低频活跃')

#: 演示账号统一口令的 BCrypt 哈希（明文 123456）。
#: 只在本地演示库里用；正式环境由后台重置密码生成。
DEMO_PASSWORD_HASH = '$2b$10$9ch6/n2uPdZz9kjuXdCtAOxa/gq8uE7ExOrIcw7IPiqN94Y2y9Ngu'

#: 套餐种子，与 `frontend/src/api/mock/package.js` 逐字一致。
#: 业务表尚未有真实数据，先由 ETL 灌入；套餐分布要 join 它才能出名字。
PACKAGE_SEED = (
    (1, '畅享 5G 39 元套餐', 39.00, 30.00, 300, 100,
     '30GB 国内流量，300 分钟国内通话，超出后 0.29 元/分钟。', 1, '2024-01-05 09:00:00'),
    (2, '畅享 5G 79 元套餐', 79.00, 60.00, 600, 200,
     '60GB 国内流量，600 分钟国内通话，赠送 5G 极速服务。', 1, '2024-01-05 09:00:00'),
    (3, '畅享 5G 129 元套餐', 129.00, 100.00, 1000, 300,
     '100GB 国内流量，1000 分钟国内通话，附赠 1 张副卡。', 1, '2024-01-05 09:00:00'),
    (4, '孝心卡 19 元套餐', 19.00, 5.00, 200, 50,
     '面向老年用户，5GB 流量 + 200 分钟通话，支持设置亲情号码。', 1, '2026-02-11 14:30:00'),
    (5, '学生特惠 29 元套餐', 29.00, 40.00, 100, 100,
     '校园专项，40GB 定向流量，需通过学生身份认证。', 0, '2026-03-02 10:00:00'),
)


# ===========================================================================
# 二、校验
# ===========================================================================

def parse_time(value):
    """
    返回 (datetime_utc, fail_rule)。规则 3 与规则 4 都落在这一处。

    非 ISO8601 字面量判**类型错误**（规则 3）；格式对但落在未来 / 早于
    上线日判**时间越界**（规则 4）。两者混为一谈会让子页的规则分布失真。
    """
    if not isinstance(value, str) or not TIME_RE.match(value):
        return None, 'TYPE_ERROR'
    try:
        dt = datetime.strptime(value, '%Y-%m-%dT%H:%M:%S.%fZ').replace(
            tzinfo=timezone.utc)
    except ValueError:
        return None, 'TYPE_ERROR'
    return dt, None


def valid_uuid(value):
    return isinstance(value, str) and UUID_RE.match(value) is not None


def valid_ip(value):
    """规则 5：先校形状（四段点分），再逐段校 0—255。"""
    if value is None:
        return True                       # ip 可空
    if not isinstance(value, str):
        return False
    m = IPV4_RE.match(value)
    return m is not None and all(0 <= int(part) <= 255 for part in m.groups())


def page_bad(value):
    """规则 7：超过 64 字符，或含控制字符 / U+FFFD 替换字符。"""
    if not isinstance(value, str):
        return True
    if len(value) > 64:
        return True
    return any(ord(ch) < 32 or ord(ch) == 127 or ch == '�'
               for ch in value)


def validate(row, now, seen):
    """
    按七条规则逐条判定。返回 (fail_rule, event_date)。

    `fail_rule` 为 None 表示干净行。`seen` 是所有已通过前 5 条规则的
    (event_id, event_date) 集合，用于规则 6——第一条正常入库，后续入死信。
    """
    # ① 必填字段缺失
    for field in REQUIRED_FIELDS:
        if row.get(field) is None or row.get(field) == '':
            return 'MISSING_FIELD', None

    # ② 枚举越界
    if row['event_code'] not in EVENT_CODES:
        return 'ENUM_OUT_OF_RANGE', None
    if row['device'] not in DEVICES:
        return 'ENUM_OUT_OF_RANGE', None

    # ③ 字段类型错误（user_id 传成字符串；event_time 非 ISO8601）
    uid = row.get('user_id')
    if uid is not None and (not isinstance(uid, int) or isinstance(uid, bool)):
        return 'TYPE_ERROR', None
    event_time, err = parse_time(row['event_time'])
    if err:
        return err, None

    # ④ 时间戳越界（未来 / 早于上线日）
    if event_time > now or event_time < SYSTEM_ONLINE_DATE:
        return 'TIME_OUT_OF_RANGE', None
    event_date = event_time.astimezone(CHINA_TZ).date()

    # ⑤ 标识格式非法
    if not valid_uuid(row['event_id']):
        return 'BAD_IDENTIFIER', None
    if not valid_uuid(row['session_id']):
        return 'BAD_IDENTIFIER', None
    if not valid_ip(row.get('ip')):
        return 'BAD_IDENTIFIER', None

    # ⑥ 重复上报（与 uk_event (event_id, event_date) 同口径）
    key = (row['event_id'], event_date)
    if key in seen:
        return 'DUPLICATE', None
    seen.add(key)

    # ⑦ 字段超长 / 乱码
    if page_bad(row['page']):
        # 这一行已经进了 seen，但 event_id 本身合法。重复行是无害的，
        # 因为造数脚本不会去复制一条已判脏的行。
        return 'TOO_LONG_OR_MOJIBAKE', None

    return None, event_date


# ===========================================================================
# 三、读取与装载
# ===========================================================================

def read_users(path):
    with open(path, encoding='utf-8') as f:
        users = json.load(f)
    if not isinstance(users, list) or not users:
        raise SystemExit('[错误] users.json 为空或格式不对')
    return users


def read_log_files(out_dir):
    files = sorted(
        name for name in os.listdir(out_dir)
        if name.startswith('behavior_') and name.endswith('.jsonl'))
    if not files:
        raise SystemExit('[错误] %s 下没有 behavior_*.jsonl' % out_dir)
    return [os.path.join(out_dir, name) for name in files]


def file_day(path):
    """从文件名取北京日期：behavior_2026-10-19.jsonl → date(2026, 10, 19)。"""
    name = os.path.basename(path)
    return datetime.strptime(name[len('behavior_'):-len('.jsonl')],
                             '%Y-%m-%d').date()


def reset_tables(conn):
    with conn.cursor() as cur:
        cur.execute('TRUNCATE TABLE dwd_behavior_log')
        cur.execute('TRUNCATE TABLE dwd_behavior_dirty')
        cur.execute('TRUNCATE TABLE dws_metric_offline')
        cur.execute('DELETE FROM t_account')
        cur.execute('DELETE FROM t_package')


def seed_business(conn, users):
    with conn.cursor() as cur:
        cur.executemany(
            'INSERT INTO t_package (id, name, monthly_fee, data_quota, '
            'voice_quota, sms_quota, description, status, create_time) '
            'VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)', PACKAGE_SEED)

        rows = []
        for u in users:
            rows.append((
                u['user_id'],
                u['login_name'],
                DEMO_PASSWORD_HASH,
                'USER',
                None,                       # id_card 本阶段不造
                u['region'],
                u['package_id'],
                None,                       # balance 本阶段不造
                1,
                u['register_date'] + ' 00:00:00',
            ))
        cur.executemany(
            'INSERT INTO t_account (id, login_name, password, role, id_card, '
            'region, package_id, balance, status, create_time) '
            'VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)', rows)


def insert_rows(conn, sql, rows, chunk=1000):
    with conn.cursor() as cur:
        for i in range(0, len(rows), chunk):
            cur.executemany(sql, rows[i:i + chunk])


# ===========================================================================
# 四、指标计算
# ===========================================================================

def compute_metrics(users, valid_rows, package_names, stat_date):
    """
    返回 dws_metric_offline 行（未落库）。

    三个用户维度分布（地域 / 套餐 / 活跃度）的分母**都是 users.json 的全量
    用户**，三者合计必须相等——这是大屏唯一的自查锚点（见 mock/screen.js）。
    """
    rows = []

    def add(code, dimension, value, metric):
        rows.append((code, dimension, str(value), stat_date, metric))

    # 地域分布：用户名册口径，逐字使用 users.json 的省份名
    region_count = Counter(u['region'] for u in users)
    for region, count in region_count.most_common():
        add('region_dist', 'region', region, count)

    # 套餐分布：按用户当前套餐；**包含已停售套餐**（不 join status = 1）
    package_count = Counter(
        package_names[u['package_id']] for u in users)
    for name, count in package_count.most_common():
        add('package_dist', 'package', name, count)

    # 活跃度分层：近 30 天干净事件数（本窗口只有 14 天）
    since = stat_date - timedelta(days=29)
    user_events = Counter()
    for row in valid_rows:
        if row['user_id'] is not None and row['event_date'] >= since:
            user_events[row['user_id']] += 1

    level_count = Counter()
    for u in users:
        n = user_events.get(u['user_id'], 0)
        if n >= ACTIVITY_HIGH:
            level_count[ACTIVITY_LABELS[0]] += 1
        elif n >= ACTIVITY_MID:
            level_count[ACTIVITY_LABELS[1]] += 1
        else:
            level_count[ACTIVITY_LABELS[2]] += 1
    for label in ACTIVITY_LABELS:       # 顺序固定，前端也按此顺序取
        add('activity_level', 'level', label, level_count[label])

    # 漏斗：**会话级去重**，不是事件条数
    session_codes = defaultdict(set)
    for row in valid_rows:
        session_codes[row['session_id']].add(row['event_code'])

    funnel_counts = {
        'view_home': 0,
        'login': 0,
        'query': 0,
        'order_package': 0,
    }
    for codes in session_codes.values():
        if 'view_home' in codes:
            funnel_counts['view_home'] += 1
        if 'login' in codes:
            funnel_counts['login'] += 1
        if codes & QUERY_EVENTS:
            funnel_counts['query'] += 1
        if 'order_package' in codes:
            funnel_counts['order_package'] += 1

    order = ('view_home', 'login', 'query', 'order_package')
    for step in order:
        add('funnel', 'step', step, funnel_counts[step])

    # 留存：以 users.json 的 register_date 为 cohort 锚点。
    # 只看**已完整度过观察期**的 cohort，算不出来的直接不落行——
    # 前端按日历补 null，绝不补 0（决策「离线指标横轴来自日历」）。
    active_by_day = defaultdict(set)
    for row in valid_rows:
        if row['user_id'] is not None:
            active_by_day[row['event_date']].add(row['user_id'])

    cohort = defaultdict(set)
    for u in users:
        cohort[datetime.strptime(u['register_date'], '%Y-%m-%d').date()].add(
            u['user_id'])

    for offset in range(13, -1, -1):
        start = stat_date - timedelta(days=offset)
        base = cohort.get(start)
        if not base:
            continue
        for kind, days in (('d1', 1), ('d7', 7)):
            target = start + timedelta(days=days)
            if target > stat_date:
                continue                # 观察期未满，不落行
            retained = len(base & active_by_day.get(target, set()))
            rate = round(retained * 100.0 / len(base), 1)
            add('retention', 'day', '%s|%s' % (start.isoformat(), kind), rate)

    return rows, level_count, funnel_counts


# ===========================================================================
# 五、主流程
# ===========================================================================

def main():
    parser = argparse.ArgumentParser(
        description='离线 ETL：mock/out → MySQL（本地演示库）')
    parser.add_argument('--input', default=os.path.join('mock', 'out'),
                        help='造数产物目录（默认 mock/out）')
    parser.add_argument('--host', default='127.0.0.1')
    parser.add_argument('--port', type=int, default=3306)
    parser.add_argument('--user', default='root')
    parser.add_argument('--password', default=os.environ.get('MYSQL_PWD', ''),
                        help='MySQL 口令；也可用环境变量 MYSQL_PWD')
    parser.add_argument('--database', default='demo01')
    args = parser.parse_args()

    if not args.password:
        raise SystemExit('[错误] 缺少 MySQL 口令：--password 或 MYSQL_PWD')

    users_path = os.path.join(args.input, 'users.json')
    users = read_users(users_path)
    files = read_log_files(args.input)

    package_names = {p[0]: p[1] for p in PACKAGE_SEED}

    now = datetime.now(timezone.utc)
    seen = set()
    valid_rows = []
    dirty_rows = []
    rule_hits = Counter()
    per_day = {}                        # date → [采集, 入库, 判脏]
    collected = stored = dirty = 0

    for path in files:
        day = file_day(path)
        day_collected = day_stored = day_dirty = 0
        fail_time = datetime.combine(day, datetime.min.time()) + \
            timedelta(minutes=5)        # 该日批次时间

        with open(path, encoding='utf-8') as f:
            for line in f:
                line = line.rstrip('\n')
                if not line.strip():
                    continue
                day_collected += 1
                try:
                    obj = json.loads(line)
                except json.JSONDecodeError:
                    # 文件本身坏掉不在七条规则里；按类型错误收进死信，
                    # 至少不静默丢行。
                    obj = None

                if obj is None:
                    rule, event_date = 'TYPE_ERROR', None
                else:
                    rule, event_date = validate(obj, now, seen)

                if rule is not None:
                    code = RULE_CODE[rule]
                    rule_hits[code] += 1
                    dirty_rows.append((code, line, fail_time))
                    day_dirty += 1
                    continue

                # 文件按北京日期切分；ETL 按 event_time 换算出的日期必须一致。
                if event_date != day:
                    raise SystemExit(
                        '[错误] %s 中存在 event_date=%s 的行，与文件日期不符'
                        % (os.path.basename(path), event_date))

                utc_dt = parse_time(obj['event_time'])[0].replace(tzinfo=None)
                ext = obj.get('ext')
                valid_rows.append({
                    'event_id': obj['event_id'],
                    'event_date': event_date,
                    'event_code': obj['event_code'],
                    'event_time': utc_dt,
                    'user_id': obj.get('user_id'),
                    'session_id': obj['session_id'],
                    'page': obj['page'],
                    'region': obj['region'],
                    'device': obj['device'],
                    'ip': obj.get('ip'),
                    'ext': (json.dumps(ext, ensure_ascii=False)
                            if ext is not None else None),
                })
                day_stored += 1

        per_day[day] = (day_collected, day_stored, day_dirty)
        collected += day_collected
        stored += day_stored
        dirty += day_dirty

    stat_date = max(per_day)

    # ---- 自检（不通过就不落库） ------------------------------------------
    problems = []
    if stored + dirty != collected:
        problems.append('采集 ≠ 入库 + 判脏')
    if sum(rule_hits.values()) != dirty:
        problems.append('七条规则命中合计 ≠ 判脏行数')
    if len(valid_rows) != stored:
        problems.append('内存干净行数 ≠ 入库计数')

    metric_rows, level_count, funnel = compute_metrics(
        users, valid_rows, package_names, stat_date)
    total_users = len(users)
    dist_sums = {
        'region_dist': sum(r[4] for r in metric_rows
                           if r[0] == 'region_dist'),
        'package_dist': sum(r[4] for r in metric_rows
                            if r[0] == 'package_dist'),
        'activity_level': sum(level_count.values()),
    }
    for name, value in dist_sums.items():
        if int(value) != total_users:
            problems.append('%s 合计 %s ≠ 用户总数 %s'
                            % (name, value, total_users))
    steps = [funnel[s] for s in
             ('view_home', 'login', 'query', 'order_package')]
    if steps != sorted(steps, reverse=True):
        problems.append('漏斗不是逐段递减：%s' % steps)

    print('用户 %d 人；文件 %d 个（%s ~ %s）'
          % (len(users), len(files), min(per_day), max(per_day)))
    print('采集 %d = 入库 %d + 判脏 %d（%.2f%%）'
          % (collected, stored, dirty, dirty * 100.0 / collected))
    print('规则命中：' + ' / '.join(
        '%s=%d' % (k, rule_hits[k]) for k in RULE_CODE.values()))
    print('漏斗（会话级）：view_home %d → login %d → query %d → order_package %d'
          % (steps[0], steps[1], steps[2], steps[3]))
    print('活跃度：高频 %d / 中频 %d / 低频 %d'
          % (level_count[ACTIVITY_LABELS[0]],
             level_count[ACTIVITY_LABELS[1]],
             level_count[ACTIVITY_LABELS[2]]))

    if problems:
        for p in problems:
            print('[自检失败] ' + p)
        raise SystemExit(1)

    # ---- 落库 ------------------------------------------------------------
    conn = pymysql.connect(
        host=args.host, port=args.port, user=args.user,
        password=args.password, database=args.database,
        charset='utf8mb4', autocommit=False)
    try:
        reset_tables(conn)
        seed_business(conn, users)

        insert_rows(
            conn,
            'INSERT INTO dwd_behavior_log (event_id, event_date, event_code, '
            'event_time, user_id, session_id, page, region, device, ip, ext) '
            'VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)',
            [(r['event_id'], r['event_date'], r['event_code'],
              r['event_time'], r['user_id'], r['session_id'], r['page'],
              r['region'], r['device'], r['ip'], r['ext'])
             for r in valid_rows])

        insert_rows(
            conn,
            'INSERT INTO dwd_behavior_dirty (source, raw_content, '
            'fail_reason, fail_time) VALUES (%s, %s, %s, %s)',
            [('OFFLINE', raw, reason, ft)
             for reason, raw, ft in dirty_rows])

        insert_rows(
            conn,
            'INSERT INTO dws_metric_offline (metric_code, dimension, '
            'dimension_value, stat_date, metric_value) '
            'VALUES (%s, %s, %s, %s, %s) '
            'ON DUPLICATE KEY UPDATE metric_value = VALUES(metric_value)',
            metric_rows)
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()

    print('已写入：t_account %d / t_package %d / dwd_behavior_log %d / '
          'dwd_behavior_dirty %d / dws_metric_offline %d'
          % (len(users), len(PACKAGE_SEED), len(valid_rows),
             len(dirty_rows), len(metric_rows)))
    print('统计日 stat_date = %s' % stat_date)


if __name__ == '__main__':
    main()
