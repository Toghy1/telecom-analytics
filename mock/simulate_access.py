#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
模拟用户访问脚本 —— 造 500 个模拟用户，按「注册日 + 回访概率」生成行为，
再按《开发文档》§5.4 注入合计 8% 的脏数据。

**每人条数不是固定的**：用户先定一个注册日，之后按活跃档位以不同概率回访。
只来注册那天的低频用户不到 10 条，天天回来的高频用户能到上百条 ——
大屏的「活跃度分层」与「次日 / 7 日留存」正是靠这层差异才画得出来。

产出（默认写到 mock/out/）：

    users.json                 500 个用户：id / 手机号 / 归属地 / 套餐 / 注册日
    behavior_YYYY-MM-DD.jsonl  按天切分的行为日志：登录用户行为 + 游客会话

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
  大屏漏斗会出现「查询 > 登录」这种往上翘的一级。脚本末尾有会话级漏斗自检，
  不满足「逐段不增」直接报错退出，不会悄悄产出一份错数据。
· 游客会话**单独造**（--guest-sessions，默认 20000）：游客的 view_home 行
  user_id 为 null，其中 --guest-convert（默认 0.05）比例的会话随后 login
  转成真实用户会话。登录用户会话一律 login 打头，**只靠它们漏斗第一段永远
  小于第二段** —— 游客是漏斗第一段的唯一来源，删不得。
· 登录用户按**活跃档位**生成（HIGH / MID / LOW）：先定注册日，再按档位以
  「次日 / 第 7 日 / 其他日」三个回访概率决定后续来不来。旧版「每人凑满
  100 条、日期均匀随机」的后果是人人天天在线 —— 活跃度只有一层、留存
  90% 以上，图表照样画出来，但什么也说明不了。
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

#: 漏斗第三段「查询」由三个查询事件合并而成（前端 FUNNEL_STEP 里的 query）。
QUERY_EVENTS = frozenset(('query_balance', 'query_bill', 'query_usage'))

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

#: 用户活跃档位：
#: (档位名, 权重, 注册当天会话数, 回访日会话数, 次日回访率, 第 7 日回访率, 其他日回访率)
#:
#: 三个回访概率是**留存曲线的直接来源**：d1 留存 ≈ 三档 p1 的加权平均（约 47%），
#: d7 ≈ p7 的加权平均（约 27%）。改这里等于改图上那两条线。
#: 「注册当天会话数」与回访次数一起决定每人总条数，进而决定活跃度分层：
#:   LOW  只来注册那天、1 个会话 → 通常不到 10 条
#:   MID  偶尔回访             → 十几到几十条
#:   HIGH 大部分日子都回来      → 上百条
ACTIVITY_TIERS = (
    ('HIGH', 30, (3, 4), (2, 3), 0.80, 0.55, 0.55),
    ('MID',  45, (1, 2), (1, 2), 0.45, 0.20, 0.12),
    ('LOW',  25, (1, 1), (1, 1), 0.10, 0.05, 0.03),
)

#: 游客会话（没有账号、不登录）的归属地。游客没有 user 记录，漏斗第一段
#: 的 region 维度只能写固定值；上海市是项目演示的主场景。
GUEST_REGION = '上海市'

#: 游客登录前能逛的页面 —— 只放不需要账号的「套餐 / 活动」两个入口。
#: 首页 view_home 由 guest_session() 打头，不在池子里。
GUEST_ACTION_WEIGHTS = (
    ('view_package_list', 55),
    ('view_promo', 45),
)

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


def parse_iso(s):
    """把 iso() 写出的 UTC 串解析回带时区的 datetime。"""
    return datetime.strptime(s, '%Y-%m-%dT%H:%M:%S.%fZ').replace(
        tzinfo=timezone.utc)


def shift_into_day(events, day):
    """
    会话若跨过北京午夜，整体前移，保证每一行都落在 day 这一天。

    ⚠️ 不处理的话，23:50 开的会话会溢到第二天 —— `--end` 默认昨天，
       产出里就凭空多出一个「今天」的日志文件，和「今天归实时链路写」
       的口径打架，批量链路还会把这份不完整的当天数据当一天去算。
       也不能只丢掉溢出的几行：会话尾巴上的查询/办理一丢，漏斗后两段
       就凭空少一截。所以整段往前挪，会话结构保持不变。
    """
    last = parse_iso(events[-1]['event_time'])
    day_start = datetime(day.year, day.month, day.day, tzinfo=CHINA_TZ)
    overflow = (last - (day_start + timedelta(days=1))).total_seconds() + 1
    if overflow <= 0:
        return
    delta = timedelta(seconds=overflow)
    for ev in events:
        t = parse_iso(ev['event_time']) - delta
        ev['event_time'] = iso(t)
        ev['_day'] = t.astimezone(CHINA_TZ).date().isoformat()


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


def session_start(rng, day):
    """在某天按 24 小时活跃权重抽一个会话起始时刻（北京时间）。"""
    hour = rng.choices(range(24), weights=HOUR_WEIGHTS, k=1)[0]
    return datetime(day.year, day.month, day.day, hour,
                    rng.randint(0, 59), rng.randint(0, 59),
                    rng.randint(0, 999) * 1000, tzinfo=CHINA_TZ)


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
    shift_into_day(events, start_dt.date())
    return events


def build_events_for_user(rng, user, days, end_day, ticket_counter):
    """
    按「注册日 + 回访概率」给一个用户造行为。

    注册日存在 users.json（窗口内均匀分布），先抽活跃档位，再逐日决定来不来：
    注册当天必来，之后第 1 天按 p1、第 7 天按 p7、其余日子按 p_other 回访。
    每个活跃日开 1—3 个会话，会话内部仍是 login → 动作 → (可能) logout。

    ⚠️ 注册日靠后的用户没有足够的「明天」可回访，d1 / d7 天然算不出来 ——
       这不是数据缺了，而是「还没到那天」，后端必须留 null、不能补 0。
    """
    _, _, reg_sessions, back_sessions, p1, p7, p_other = rng.choices(
        ACTIVITY_TIERS, weights=[t[1] for t in ACTIVITY_TIERS], k=1)[0]
    reg_day = datetime.strptime(user['register_date'], '%Y-%m-%d').date()
    reg_offset = (end_day - reg_day).days

    events = []
    for n in range(reg_offset + 1):
        if n == 0:
            n_sessions = rng.randint(*reg_sessions)
        else:
            p = p1 if n == 1 else (p7 if n == 7 else p_other)
            if rng.random() >= p:
                continue
            n_sessions = rng.randint(*back_sessions)

        day = reg_day + timedelta(days=n)
        for _ in range(n_sessions):
            sid = str(uuid.UUID(int=rng.getrandbits(128)))
            events.extend(session_events(
                rng, user, sid, session_start(rng, day), ticket_counter))

    # 按时间排一遍 —— 会话是按随机的天/点开的，不排的话日志里时间乱跳
    events.sort(key=lambda e: e['event_time'])
    return events


def guest_session(rng, users, users_by_id, active_by_day, session_id, start_dt,
                  ticket_counter, convert_ratio):
    """
    生成一个游客会话：view_home →（可能逛套餐/活动）→（可能）login 转正式会话。

    游客行的 user_id 是 None（落盘成 null）。漏斗按**会话数**统计，不登录的
    游客止步第一段，第一段才可能大于第二段。转登录后沿用**同一个 session_id**
    ——换成新 id 的话这个会话就只算过「访问」、没算过「登录」，等于没转化。
    region / device / ip 从登录那一刻起换成用户自己的，和真实登录一致。

    ⚠️ 转化对象**只能从「当天本来就有行为的用户」里挑**（其次才是当天
       已注册的用户）。挑完全随机的人等于给每个人凭空加一天活跃 ——
       20 000 个游客会话 × 35% ≈ 7 000 次登录摊到 500 人头上，
       人均每天一次，活跃度与留存立刻又退化成「人人天天在线」。

    返回 (事件列表, 是否发生了登录转化)。
    """
    device = pick(rng, DEVICES)
    ip = rand_ip(rng)
    events = []
    t = start_dt

    def emit(code, user):
        nonlocal t
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
            'user_id': user['user_id'] if user else None,
            'session_id': session_id,
            'page': EVENT_PAGE[code],
            'region': user['region'] if user else GUEST_REGION,
            'device': user['device'] if user else device,
            'ip': user['ip'] if user else ip,
            'ext': ext,
            '_day': t.date().isoformat(),
        })
        t += timedelta(seconds=rng.randint(5, 240))

    emit('view_home', None)
    for _ in range(rng.randint(0, 2)):
        emit(pick(rng, GUEST_ACTION_WEIGHTS), None)

    day_iso = start_dt.date().isoformat()
    pool = active_by_day.get(day_iso)
    if pool:
        user = users_by_id[rng.choice(tuple(pool))]
    else:
        eligible = [u for u in users if u['register_date'] <= day_iso]
        user = rng.choice(eligible) if eligible else None
    converted = user is not None and rng.random() < convert_ratio
    if converted:
        emit('login', user)
        for _ in range(rng.randint(*SESSION_ACTIONS)):
            emit(pick(rng, ACTION_WEIGHTS), user)
        if rng.random() < LOGOUT_RATIO:
            emit('logout', user)
    shift_into_day(events, start_dt.date())
    return events, converted


def build_guest_sessions(rng, users, users_by_id, active_by_day, count, days,
                         end_day, ticket_counter, convert_ratio):
    """
    造 count 个游客会话，起始时间与登录用户同样按「随机一天 + 小时峰谷」抽。

    游客行是**额外**的，不计入登录用户的行为 —— 混进来会搅乱活跃度分层
    与留存 cohort（游客没有稳定的注册日，不属于任何 cohort）。

    返回 (事件列表, 登录转化会话数)。
    """
    events = []
    n_converted = 0
    for _ in range(count):
        day = end_day - timedelta(days=rng.randrange(days))
        sid = str(uuid.UUID(int=rng.getrandbits(128)))
        evs, converted = guest_session(
            rng, users, users_by_id, active_by_day, sid,
            session_start(rng, day), ticket_counter, convert_ratio)
        events.extend(evs)
        n_converted += int(converted)
    events.sort(key=lambda e: e['event_time'])
    return events, n_converted


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
# 五、漏斗自检
# ===========================================================================

def check_funnel(rows):
    """
    会话级漏斗自检：view_home → login → query_* → order_package 逐段不增。

    按 session_id **去重**计数，不是按事件条数 —— 同一个人在一个会话里
    查了三次话费，漏斗第三段仍然只算 1；大屏后端也是这么聚集的。
    返回四段的会话数。
    """
    home, login, query, order = set(), set(), set(), set()
    for ev in rows:
        sid = ev['session_id']
        code = ev['event_code']
        if code == 'view_home':
            home.add(sid)
        elif code == 'login':
            login.add(sid)
        elif code in QUERY_EVENTS:
            query.add(sid)
        elif code == 'order_package':
            order.add(sid)
    return len(home), len(login), len(query), len(order)


# ===========================================================================
# 六、入口
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
    ap.add_argument('--days', type=int, default=14,
                    help='回溯天数（默认 14；7 日留存至少要 14 天）')
    ap.add_argument('--end', default=None,
                    help='结束日期 YYYY-MM-DD，默认昨天（今天归实时链路写）')
    ap.add_argument('--out', default='out', help='输出目录（默认 out）')
    ap.add_argument('--seed', type=int, default=20261010, help='随机种子，同种子可复现')
    ap.add_argument('--dirty', type=float, default=0.08,
                    help='脏数据占比（占入库总行数，默认 0.08；给 0 则不注入）')
    ap.add_argument('--guest-sessions', type=int, default=20000,
                    help='游客会话数（默认 20000；漏斗第一段只由它们贡献，给 0 会自检失败）')
    ap.add_argument('--guest-convert', type=float, default=0.05,
                    help='游客会话转登录的比例（默认 0.05；调高会抹平活跃度分层）')
    args = ap.parse_args()

    if args.users < 1:
        ap.error('--users 至少为 1')
    if args.days < 1:
        ap.error('--days 至少为 1')
    if not 0 <= args.dirty < 1:
        ap.error('--dirty 取值范围 [0, 1)')
    if args.guest_sessions < 0:
        ap.error('--guest-sessions 不能为负')
    if not 0 <= args.guest_convert <= 1:
        ap.error('--guest-convert 取值范围 [0, 1]')

    rng = random.Random(args.seed)
    end_day = (datetime.strptime(args.end, '%Y-%m-%d').date() if args.end
               else (datetime.now() - timedelta(days=1)).date())

    os.makedirs(args.out, exist_ok=True)

    users = build_users(rng, args.users)
    for u in users:
        u['ip'] = rand_ip(rng)
        # 注册日：窗口内均匀分布，是留存 cohort 的锚点，也是「注册前不能登录」
        # 这条约束的依据（游客转化只从当天及之前已注册的用户里挑）。
        u['register_date'] = (
            end_day - timedelta(days=rng.randrange(args.days))
        ).isoformat()

    # --- 逐个用户造日志，先攒成一个平铺的列表 ---
    user_rows = []
    ticket_counter = [0]
    for u in users:
        user_rows.extend(build_events_for_user(
            rng, u, args.days, end_day, ticket_counter))

    # 每人条数自检只数**用户自己的行** —— 游客转化后也会带 user_id，
    # 混进来会把这批用户算成「活跃度虚高」
    per_user = {}
    for ev in user_rows:
        per_user[ev['user_id']] = per_user.get(ev['user_id'], 0) + 1
    counts = [per_user.get(u['user_id'], 0) for u in users]
    missing = [u['user_id'] for u in users if per_user.get(u['user_id'], 0) == 0]
    hi = sum(1 for c in counts if c >= 50)
    mid = sum(1 for c in counts if 10 <= c < 50)
    lo = sum(1 for c in counts if c < 10)
    print('[自检] 每个用户都有行为 %s（%d 人；条数 %d—%d，人均 %.1f）'
          % ('✅' if not missing else '❌ %d 人没有' % len(missing),
             len(counts), min(counts), max(counts), sum(counts) / len(counts)))
    print('[自检] 活跃度分层预演（≥50 / 10—49 / <10）：%d / %d / %d'
          % (hi, mid, lo))
    if missing:
        sys.exit(1)

    # --- 游客会话：额外的行，漏斗第一段的来源 ---
    # 转化目标只从「当天本来就有行为的用户」里挑，避免凭空增加活跃日
    users_by_id = {u['user_id']: u for u in users}
    active_by_day = {}
    for ev in user_rows:
        active_by_day.setdefault(ev['_day'], set()).add(ev['user_id'])

    guest_rows, n_converted = build_guest_sessions(
        rng, users, users_by_id, active_by_day, args.guest_sessions,
        args.days, end_day, ticket_counter, args.guest_convert)

    rows = user_rows + guest_rows
    base_rows = len(rows)

    # --- 注入脏数据 ---
    hits, n_dup = inject_dirty(rng, rows, args.dirty)
    total_rows = len(rows)

    # --- 漏斗自检：注入后仍须逐段不增，否则不落盘、直接退出 ---
    f_home, f_login, f_query, f_order = check_funnel(rows)
    funnel_ok = f_home >= f_login >= f_query >= f_order
    print('[漏斗自检] 访问 %d → 登录 %d → 查询 %d → 办理 %d 会话 %s'
          % (f_home, f_login, f_query, f_order,
             '✅' if funnel_ok else '❌ 不是逐段不增，已中止，未覆盖输出目录'))
    if not funnel_ok:
        sys.exit(1)

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
    print('[日志] 用户行为 %d 条（%d 人，人均 %.1f 条）'
          % (len(user_rows), args.users, len(user_rows) / args.users))
    print('[日志] 游客 %d 个会话 → %d 条（登录转化 %d 个，%.1f%%）'
          % (args.guest_sessions, len(guest_rows), n_converted,
             (100.0 * n_converted / args.guest_sessions
              if args.guest_sessions else 0.0)))
    print('[日志] 基础记录合计 %d 条' % base_rows)

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
