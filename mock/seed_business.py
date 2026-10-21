#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
业务库种子数据 —— 把前端 `src/api/mock/` 里那套假数据原样落进 MySQL。

**为什么要有这个脚本**：前端接线后 `api/*.js` 不再读 mock，数据从
`backend/business` 查库。库若是空的，页面不是报错而是**全空**——验收时
分不清「接口没通」和「表里没数据」。这套种子把假数据阶段每一页的样子
原封不动搬到库里，于是「接线前后页面看起来一样」本身就成了验收标准。

产出：默认写到 `mock/out/business_seed.sql`（`mock/.gitignore` 已忽略 out/），
再用 mysql 客户端导入：

    python mock/seed_business.py
    mysql -uroot -p --default-character-set=utf8mb4 demo01 \\
        < mock/out/business_seed.sql

**幂等**：全部用显式主键 + `ON DUPLICATE KEY UPDATE`，重复导入不会翻倍。
⚠️ 显式主键是刻意的：演示账号固定 1001，分析员 2001，管理员 3001，
其余假数据（1002 的流水等）都按这几个 id 咬合；交给 AUTO_INCREMENT
自己发号，换台机器重跑一次号段就全变了。

**数据的证伪条件**（每一条都对应一类实现错误，改动时别删）：
  · 1002 的账单 / 用量 / 工单 / 办理记录 —— 证伪「查询漏了 WHERE user_id」
  · 促销 id=3（status=0）、id=4（status=1 但档期已过）、
    id=6（status=1 但还没开始）—— 证伪「只看 status」与「只判结束时间」
  · 办理记录里含已下架的套餐 5 —— 证伪「解析套餐名时误用『在售套餐列表』」
  · 套餐 5 的 status=0 且月费 29 排在第二 —— 证伪「在售列表漏过滤」
  · 工单三个状态、四个类型齐全 —— 状态/类型的翻译写错一眼能比对出来
"""

import argparse
import pathlib
import sys

# 500 个演示用户的共同口令哈希（明文 123456）。BCrypt 每次加盐结果不同，
# 但哈希本身可以复用——matches() 比的是哈希，不是加密过程。
PASSWORD_HASH = "$2b$10$9ch6/n2uPdZz9kjuXdCtAOxa/gq8uE7ExOrIcw7IPiqN94Y2y9Ngu"

DEMO_USER_ID = 1001
ANALYST_ID = 2001
ADMIN_ID = 3001


def q(value):
    """SQL 字面量。None → NULL；字符串里的单引号与反斜杠转义。"""
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "1" if value else "0"
    if isinstance(value, (int, float)):
        return str(value)
    return "'" + str(value).replace("\\", "\\\\").replace("'", "''") + "'"


# ---------------------------------------------------------------------------
# 套餐。库里已有 5 条（id 1..5），这里重写一遍是为了让全新库也能一次建齐。
# 名称、月费、三个量纲与前端 `mock/package.js` 逐字一致。
# ---------------------------------------------------------------------------
PACKAGES = [
    (1, "畅享 5G 39 元套餐", 39.00, 30.00, 300, 100,
     "30GB 国内流量，300 分钟国内通话，超出后 0.29 元/分钟。", 1, "2024-01-05 09:00:00"),
    (2, "畅享 5G 79 元套餐", 79.00, 60.00, 600, 200,
     "60GB 国内流量，600 分钟国内通话，赠送 5G 极速服务。", 1, "2024-01-05 09:00:00"),
    (3, "畅享 5G 129 元套餐", 129.00, 100.00, 1000, 300,
     "100GB 国内流量，1000 分钟国内通话，附赠 1 张副卡。", 1, "2024-01-05 09:00:00"),
    (4, "孝心卡 19 元套餐", 19.00, 5.00, 200, 50,
     "5GB 国内流量，200 分钟国内通话，面向老年用户，含亲情号码。", 1, "2026-02-01 09:00:00"),
    # ⚠️ status=0：已停售的历史套餐。在售列表必须过滤掉它，办理记录里却要能显示名字。
    (5, "学生特惠 29 元套餐", 29.00, 40.00, 100, 100,
     "40GB 国内流量，100 分钟国内通话，凭学生证办理，每年 7-8 月暂停受理。", 0, "2026-03-02 09:00:00"),
]

# ---------------------------------------------------------------------------
# 账号。
#   · 1001 是演示账号：手机号登录，当前 79 元套餐（与办理记录末条、账单月费咬合）
#   · 2001 / 3001 是分析员与管理员：**不填身份证、套餐、余额**（最小必要原则），
#     用户名登录（`analyst` / `admin`），脱敏规则不适用——后端按非 11 位原样返回
# ---------------------------------------------------------------------------
ACCOUNTS = [
    (DEMO_USER_ID, "13800001234", PASSWORD_HASH, "USER",
     "440301199001011234", "广东省深圳市", 2, 68.50, 1, "2024-03-15 10:20:00"),
    (ANALYST_ID, "analyst", PASSWORD_HASH, "ANALYST",
     None, "广东省广州市", None, None, 1, "2024-03-15 10:20:00"),
    (ADMIN_ID, "admin", PASSWORD_HASH, "ADMIN",
     None, "北京市", None, None, 1, "2024-03-15 10:20:00"),
]

# ---------------------------------------------------------------------------
# 促销活动。四条来自 mock，另加两条边界陷阱。
# ---------------------------------------------------------------------------
PROMOS = [
    (1, "新装宽带立减 200 元",
     "2026 年 9 月 1 日起，新装 500M 宽带并绑定手机套餐，一次性立减 200 元。",
     2, "2026-09-01 00:00:00", "2026-12-31 23:59:59", 1),
    (2, "5G 套餐升档赠流量",
     "升档至 129 元套餐，连续 3 个月每月赠送 20GB 国内流量。",
     3, "2026-09-10 00:00:00", "2026-10-31 23:59:59", 1),
    # 陷阱①：status=0（运营已下架）
    (3, "老用户专享 9 折",
     "在网满 5 年的用户，办理任意套餐享 9 折优惠。",
     1, "2026-06-01 00:00:00", "2026-06-30 23:59:59", 0),
    # 陷阱②：status 忘了置 0，但档期早就过了（证伪「只过滤 status」）
    (4, "暑期流量包 5 折",
     "暑期特惠，10GB 流量包 5 折，每人限购一次。",
     1, "2026-06-01 00:00:00", "2026-08-31 23:59:59", 1),
    # 陷阱③：status=1、档期还没开始（证伪「只判结束时间」）
    (6, "双十一流量狂欢",
     "11 月 1 日起，30GB 流量包 6 折，限时 11 天。",
     2, "2026-11-01 00:00:00", "2026-11-11 23:59:59", 1),
    (5, "国庆宽带提速礼包",
     "10 月 1 日起新装宽带免费提速至 1000M，合约期 12 个月。",
     2, "2026-10-01 00:00:00", "2026-12-31 23:59:59", 1),
]

# ---------------------------------------------------------------------------
# 费用流水。金额一律正数，方向由 type 区分；bill_month 只有消费有值。
# 月费固定 79.00（当前套餐 2），超套费只出现在 2026-07——因为只有那个月
# 短信用量（214）真的超过额度（200）。两张表是同一件事的两面。
# ---------------------------------------------------------------------------
BILLS = [
    # 缴费
    (5001, DEMO_USER_ID, "PAY", 100.00, "2026-03-12 09:41:20", None, "微信充值"),
    (5002, DEMO_USER_ID, "PAY", 100.00, "2026-04-15 19:02:55", None, "支付宝充值"),
    (5003, DEMO_USER_ID, "PAY", 50.00, "2026-05-18 12:33:41", None, "微信充值"),
    (5004, DEMO_USER_ID, "PAY", 100.00, "2026-06-20 08:12:09", None, "中国电信 APP 充值"),
    (5005, DEMO_USER_ID, "PAY", 100.00, "2026-07-22 21:47:16", None, "支付宝充值"),
    (5006, DEMO_USER_ID, "PAY", 100.00, "2026-08-20 14:31:09", None, "微信充值"),
    (5007, DEMO_USER_ID, "PAY", 50.00, "2026-09-05 09:26:33", None, "微信充值"),
    # 2026-05 账期（合计 85.00，2 笔）
    (5008, DEMO_USER_ID, "BILL", 79.00, "2026-05-01 00:04:02", "2026-05", "2026 年 5 月套餐月费"),
    (5009, DEMO_USER_ID, "BILL", 6.00, "2026-05-01 00:06:37", "2026-05", "增值业务-来电显示"),
    # 2026-06 账期（合计 85.00，2 笔）
    (5010, DEMO_USER_ID, "BILL", 79.00, "2026-06-01 00:03:58", "2026-06", "2026 年 6 月套餐月费"),
    (5011, DEMO_USER_ID, "BILL", 6.00, "2026-06-01 00:06:20", "2026-06", "增值业务-来电显示"),
    # 2026-07 账期（合计 86.40，3 笔）
    (5012, DEMO_USER_ID, "BILL", 79.00, "2026-07-01 00:03:55", "2026-07", "2026 年 7 月套餐月费"),
    (5013, DEMO_USER_ID, "BILL", 6.00, "2026-07-01 00:06:31", "2026-07", "增值业务-来电显示"),
    (5014, DEMO_USER_ID, "BILL", 1.40, "2026-07-14 11:02:47", "2026-07", "国内短信超套餐部分"),
    # 2026-08 账期（合计 85.00，2 笔）
    (5015, DEMO_USER_ID, "BILL", 79.00, "2026-08-01 00:04:03", "2026-08", "2026 年 8 月套餐月费"),
    (5016, DEMO_USER_ID, "BILL", 6.00, "2026-08-01 00:06:12", "2026-08", "增值业务-来电显示"),
    # 2026-09 账期（合计 123.60，6 笔）——两页明细，用于验证分页
    (5017, DEMO_USER_ID, "BILL", 79.00, "2026-09-01 00:05:12", "2026-09", "2026 年 9 月套餐月费"),
    (5018, DEMO_USER_ID, "BILL", 6.00, "2026-09-01 00:07:40", "2026-09", "增值业务-来电显示"),
    (5019, DEMO_USER_ID, "BILL", 5.00, "2026-09-01 00:08:15", "2026-09", "增值业务-彩铃"),
    (5020, DEMO_USER_ID, "BILL", 10.00, "2026-09-06 10:24:36", "2026-09", "增值业务-和彩云网盘会员"),
    (5021, DEMO_USER_ID, "BILL", 15.60, "2026-09-15 21:40:02", "2026-09", "国际及港澳台长途通话费"),
    (5022, DEMO_USER_ID, "BILL", 8.00, "2026-09-20 08:15:47", "2026-09", "增值业务-视频彩铃"),
    # 1002 的流水：任何页面都不该显示。漏了 WHERE user_id 时，多出来的
    # 200 元充值与 129 元月费一眼可辨。
    (5901, 1002, "PAY", 200.00, "2026-09-10 16:05:00", None, "微信充值"),
    (5902, 1002, "BILL", 129.00, "2026-09-01 00:05:30", "2026-09", "2026 年 9 月套餐月费"),
]

# ---------------------------------------------------------------------------
# 套餐办理记录。末条必须落在 t_account.package_id = 2 上，否则「当前套餐」
# 与「办理记录」最新一条自相矛盾。第 5 条用已下架的套餐 5，用来证伪
# 「解析套餐名时误用在售列表」。
# ---------------------------------------------------------------------------
ORDERS = [
    (9001, DEMO_USER_ID, 1, "2024-03-15 10:22:41", 1),
    (9002, DEMO_USER_ID, 2, "2024-09-01 09:15:08", 1),
    (9003, DEMO_USER_ID, 3, "2025-03-01 10:05:33", 1),
    (9004, DEMO_USER_ID, 1, "2025-09-01 09:00:12", 1),
    (9005, DEMO_USER_ID, 5, "2026-04-15 14:32:20", 1),  # 套餐 5 现已下架
    (9006, DEMO_USER_ID, 2, "2026-06-01 09:10:05", 1),  # 当前套餐
    # 1002：第一条时间最新，漏了 user_id 过滤时会直接出现在第一页第一行
    (9901, 1002, 3, "2026-08-20 14:00:00", 1),
    (9902, 1002, 4, "2025-05-10 11:00:00", 1),
]

# ---------------------------------------------------------------------------
# 工单。三态不变量：PENDING → handler/handle_time 都空；PROCESSING → 有
# handler、无 handle_time；DONE → 两者都有，且 handle_time 不早于 create_time。
# handler_id 一律填 3001（真正的管理员账号）——mock 里写的是 2，那是个
# 不存在的 id，页面上会显示成「未知处理人」。
# ---------------------------------------------------------------------------
TICKETS = [
    (7001, DEMO_USER_ID, "NETWORK",
     "家中宽带每天 20 点后频繁掉线，重启光猫只能恢复十来分钟。",
     "PENDING", None, "2026-09-28 17:55:02", None),
    (7002, DEMO_USER_ID, "BILLING",
     "9 月账单里有一笔 30 元的增值业务费，我没有订购过，想确认来源。",
     "PENDING", None, "2026-09-24 20:15:33", None),
    (7003, DEMO_USER_ID, "COMPLAINT",
     "营业厅自助机前排队时间过长，工作日上午人也很多，希望能增加窗口。",
     "PROCESSING", ADMIN_ID, "2026-09-18 09:27:44", None),
    (7004, DEMO_USER_ID, "CONSULT",
     "咨询副卡如何办理，需要本人到营业厅吗？月费是多少？",
     "DONE", ADMIN_ID, "2026-08-12 10:03:21", "2026-08-13 14:20:05"),
    (7005, DEMO_USER_ID, "NETWORK",
     "手机在郊区老家经常没有信号，同行的其他运营商手机可以正常使用。",
     "DONE", ADMIN_ID, "2026-07-05 15:40:09", "2026-07-06 10:12:30"),
    (7006, DEMO_USER_ID, "BILLING",
     "6 月账单显示的套餐月费与我办理时看到的宣传不一致，想核实一下。",
     "DONE", ADMIN_ID, "2026-06-20 11:12:00", "2026-06-21 09:30:44"),
    # 1002：第一条时间全局最新，漏了 user_id 过滤会落在「我的工单」第一行
    (7901, 1002, "NETWORK",
     "宽带续费后网速比之前慢了很多，测速只有 20Mbps。",
     "PENDING", None, "2026-09-29 21:40:11", None),
    (7902, 1002, "BILLING",
     "短信验证码收不到，换了一台手机还是一样。",
     "DONE", ADMIN_ID, "2026-05-08 13:20:00", "2026-05-09 10:00:00"),
]

# ---------------------------------------------------------------------------
# 用量。一人一月一行（uk_user_month）。与账单咬合：
#   2026-07 used_sms=214 > 额度 200 → 账单里那笔 1.40 元超套费
#   2026-09 三项都没超 → 9 月账单没有超套费
# 1002 的 2026-09 是**故意混进来的**：漏了 user_id 过滤时，余量页会显示
# 别人几乎用满的 99.5GB，而不是本人的 48.75GB。
# ---------------------------------------------------------------------------
USAGE = [
    (6001, DEMO_USER_ID, "2026-05", 41.20, 388, 96, "2026-06-01 01:00:00"),
    (6002, DEMO_USER_ID, "2026-06", 47.90, 503, 121, "2026-07-01 01:00:00"),
    (6003, DEMO_USER_ID, "2026-07", 52.30, 574, 214, "2026-08-01 01:00:00"),
    (6004, DEMO_USER_ID, "2026-08", 33.60, 296, 74, "2026-09-01 01:00:00"),
    (6005, DEMO_USER_ID, "2026-09", 48.75, 452, 118, "2026-09-30 01:00:00"),
    (6901, 1002, "2026-09", 99.50, 980, 295, "2026-09-30 01:00:00"),
]


def _split(csv_text):
    """把 'a, b, c' 切成 ['a','b','c']。

    ⚠️ 别删这个：直接对字符串做 join 会把它按**字符**拆开，
    生成 `(i, d, ,,  , l, o, g, ...)` 那种 SQL，MySQL 报错位置在第 1 行，
    离真正写错的地方隔着好几层。
    """
    return [item.strip() for item in csv_text.split(",")]


def emit_insert(table, columns, rows, update_columns):
    """生成「显式主键 + ON DUPLICATE KEY UPDATE」的多行 INSERT。"""
    cols = _split(columns)
    updates = ", ".join(f"{col} = VALUES({col})" for col in _split(update_columns))
    values = ",\n  ".join("(" + ", ".join(q(v) for v in row) + ")" for row in rows)
    return (
        f"-- {table}：{len(rows)} 行\n"
        f"INSERT INTO {table} ({', '.join(cols)}) VALUES\n  {values}\n"
        f"ON DUPLICATE KEY UPDATE {updates};"
    )


def build_sql():
    parts = [
        "-- 由 mock/seed_business.py 生成，请勿手改；改脚本后重新生成。",
        "SET NAMES utf8mb4;",
        "START TRANSACTION;",
        "",

        emit_insert(
            "t_account",
            "id, login_name, password, role, id_card, region, package_id, balance, status, create_time",
            ACCOUNTS,
            # 口令也更新：种子脚本的职责就是「把演示账号恢复成可登录状态」
            "login_name, password, role, id_card, region, package_id, balance, status, create_time"),
        "",

        emit_insert(
            "t_package",
            "id, name, monthly_fee, data_quota, voice_quota, sms_quota, description, status, create_time",
            PACKAGES,
            "name, monthly_fee, data_quota, voice_quota, sms_quota, description, status, create_time"),
        "",

        emit_insert(
            "t_promo",
            "id, title, content, package_id, start_time, end_time, status",
            PROMOS,
            "title, content, package_id, start_time, end_time, status"),
        "",

        emit_insert(
            "t_bill",
            "id, user_id, type, amount, occur_time, bill_month, remark",
            [(i, u, t, a, o, m, r) for (i, u, t, a, o, m, r) in BILLS],
            # create_time 与 occur_time 同值（真实系统里二者也是同时产生的）
            "user_id, type, amount, occur_time, bill_month, remark"),
        # t_bill 有 create_time 列但种子表里没带，补齐：
        "UPDATE t_bill SET create_time = occur_time WHERE id IN (" +
        ", ".join(str(row[0]) for row in BILLS) + ");",
        "",

        emit_insert(
            "t_order",
            "id, user_id, package_id, order_time, status",
            ORDERS,
            "user_id, package_id, order_time, status"),
        "",

        emit_insert(
            "t_ticket",
            "id, user_id, type, content, status, handler_id, create_time, handle_time",
            TICKETS,
            "user_id, type, content, status, handler_id, create_time, handle_time"),
        "",

        emit_insert(
            "t_usage",
            "id, user_id, usage_month, used_data, used_voice, used_sms, create_time",
            USAGE,
            "user_id, usage_month, used_data, used_voice, used_sms, create_time"),
        "",

        "COMMIT;",
        "",
        "-- 自检：下面几条 COUNT 应与注释里的数字一致。",
        "-- 账号 3（1001/2001/3001）；1001 的 package_id 应为 2、balance 68.50",
        "SELECT id, login_name, role, package_id, balance FROM t_account "
        f"WHERE id IN ({DEMO_USER_ID}, {ANALYST_ID}, {ADMIN_ID});",
        "-- 进行中的活动应为 3 条（id 1 / 2 / 5）",
        "SELECT id, title, status, start_time, end_time FROM t_promo "
        "WHERE status = 1 AND start_time <= NOW() AND end_time >= NOW();",
        "-- 1001 的账期应为 5 个（2026-05..2026-09），不含 1002 的行",
        f"SELECT bill_month, SUM(amount) amount, COUNT(*) count FROM t_bill "
        f"WHERE user_id = {DEMO_USER_ID} AND type = 'BILL' GROUP BY bill_month ORDER BY bill_month DESC;",
    ]
    return "\n".join(parts) + "\n"


def main():
    parser = argparse.ArgumentParser(description="生成业务库种子 SQL")
    parser.add_argument("--out", default="mock/out/business_seed.sql",
                        help="输出路径（默认 mock/out/business_seed.sql，已被 .gitignore 忽略）")
    args = parser.parse_args()

    path = pathlib.Path(args.out)
    path.parent.mkdir(parents=True, exist_ok=True)
    # 显式 utf-8：Windows 默认编码是 GBK，写出来的 SQL 导入后中文会变问号
    path.write_text(build_sql(), encoding="utf-8")
    print(f"已生成 {path}（{path.stat().st_size} 字节）", file=sys.stderr)
    print("导入：mysql -uroot -p --default-character-set=utf8mb4 demo01 < " + str(path),
          file=sys.stderr)


if __name__ == "__main__":
    main()
