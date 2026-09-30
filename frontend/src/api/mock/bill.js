/**
 * 费用流水假数据。字段对齐《开发文档》§5.2 ④ `t_bill`：
 * id / user_id / type / amount / occur_time / bill_month / remark / create_time
 *
 * ⚠️ **金额一律存正数，方向靠 `type` 区分**——`BILL` 消费 / `PAY` 缴费。
 *    这是 claude.md 里明确的设计，为的是避免靠正负号表意导致聚合时算错口径。
 *    所以这里的 amount 全是正数，看到 `type: 'PAY'` 才知道那是进账。
 *
 * `bill_month` 只有消费有值（格式 `2026-09`），缴费记录该字段为 null。
 *
 * ---------------------------------------------------------------------------
 * 这份流水是**按一本能对得上的账**写出来的，改动时请一并维持这个约束：
 *
 * 1. **月费固定 79.00**——`t_account.package_id = 2` 即「畅享 5G 79 元套餐」，
 *    月费就是 79 元。曾经这里 8 月写 30.0、9 月写 79.0，同一个字段三个月三个数，
 *    摆上账单页一眼就能看出假。
 *
 * 2. **没有「超套餐」费就是没超套餐**——`t_usage` 只在 2026-07 让 `used_sms`
 *    超过 200 条，所以只有 7 月有「国内短信超套餐部分」。反过来，
 *    一旦某个月出现「流量/通话/短信超套餐部分」，那个月的 `t_usage` 就必须
 *    真的超过额度。这两张表是同一件事的两面，**分开改必出事**：
 *    余量页显示「剩余 148 分钟」而账单里躺着「国内通话超套餐部分」，
 *    是验收时最容易被抓的自相矛盾。
 *
 * 3. **其余消费项都不受套餐额度约束**——增值业务（来电显示 / 彩铃 / 视频彩铃 /
 *    和彩云网盘会员）与国际及港澳台长途，都是额度之外的独立计费项，
 *    有它们不代表超套。9 月这 6 笔就是这么凑出来的：既要有足够条数把
 *    「明细分页」跑出第二页，又不能凭空造出超套。
 *
 * 4. **混入了一条 user_id = 1002 的记录**——见文件末尾的说明。
 * ---------------------------------------------------------------------------
 */
export const mockBills = [
  /* ---------- 缴费（type = PAY，bill_month 为 null） ---------- */
  {
    id: 5001,
    user_id: 1001,
    type: 'PAY',
    amount: 100.0,
    occur_time: '2026-03-12 09:41:20',
    bill_month: null,
    remark: '微信充值',
    create_time: '2026-03-12 09:41:20'
  },
  {
    id: 5002,
    user_id: 1001,
    type: 'PAY',
    amount: 100.0,
    occur_time: '2026-04-15 19:02:55',
    bill_month: null,
    remark: '支付宝充值',
    create_time: '2026-04-15 19:02:55'
  },
  {
    id: 5003,
    user_id: 1001,
    type: 'PAY',
    amount: 50.0,
    occur_time: '2026-05-18 12:33:41',
    bill_month: null,
    remark: '微信充值',
    create_time: '2026-05-18 12:33:41'
  },
  {
    id: 5004,
    user_id: 1001,
    type: 'PAY',
    amount: 100.0,
    occur_time: '2026-06-20 08:12:09',
    bill_month: null,
    remark: '中国电信 APP 充值',
    create_time: '2026-06-20 08:12:09'
  },
  {
    id: 5005,
    user_id: 1001,
    type: 'PAY',
    amount: 100.0,
    occur_time: '2026-07-22 21:47:16',
    bill_month: null,
    remark: '支付宝充值',
    create_time: '2026-07-22 21:47:16'
  },
  {
    id: 5006,
    user_id: 1001,
    type: 'PAY',
    amount: 100.0,
    occur_time: '2026-08-20 14:31:09',
    bill_month: null,
    remark: '微信充值',
    create_time: '2026-08-20 14:31:09'
  },
  {
    id: 5007,
    user_id: 1001,
    type: 'PAY',
    amount: 50.0,
    occur_time: '2026-09-05 09:26:33',
    bill_month: null,
    remark: '微信充值',
    create_time: '2026-09-05 09:26:33'
  },

  /* ---------- 2026-05 账期（合计 85.00，2 笔） ---------- */
  {
    id: 5008,
    user_id: 1001,
    type: 'BILL',
    amount: 79.0,
    occur_time: '2026-05-01 00:04:02',
    bill_month: '2026-05',
    remark: '2026 年 5 月套餐月费',
    create_time: '2026-05-01 00:04:02'
  },
  {
    id: 5009,
    user_id: 1001,
    type: 'BILL',
    amount: 6.0,
    occur_time: '2026-05-01 00:06:37',
    bill_month: '2026-05',
    remark: '增值业务-来电显示',
    create_time: '2026-05-01 00:06:37'
  },

  /* ---------- 2026-06 账期（合计 85.00，2 笔） ---------- */
  {
    id: 5010,
    user_id: 1001,
    type: 'BILL',
    amount: 79.0,
    occur_time: '2026-06-01 00:03:58',
    bill_month: '2026-06',
    remark: '2026 年 6 月套餐月费',
    create_time: '2026-06-01 00:03:58'
  },
  {
    id: 5011,
    user_id: 1001,
    type: 'BILL',
    amount: 6.0,
    occur_time: '2026-06-01 00:06:20',
    bill_month: '2026-06',
    remark: '增值业务-来电显示',
    create_time: '2026-06-01 00:06:20'
  },

  /* ---------- 2026-07 账期（合计 86.40，3 笔） ---------- */
  {
    id: 5012,
    user_id: 1001,
    type: 'BILL',
    amount: 79.0,
    occur_time: '2026-07-01 00:03:55',
    bill_month: '2026-07',
    remark: '2026 年 7 月套餐月费',
    create_time: '2026-07-01 00:03:55'
  },
  {
    id: 5013,
    user_id: 1001,
    type: 'BILL',
    amount: 6.0,
    occur_time: '2026-07-01 00:06:31',
    bill_month: '2026-07',
    remark: '增值业务-来电显示',
    create_time: '2026-07-01 00:06:31'
  },
  {
    id: 5014,
    user_id: 1001,
    type: 'BILL',
    amount: 1.4,
    occur_time: '2026-07-14 11:02:47',
    bill_month: '2026-07',
    remark: '国内短信超套餐部分',
    create_time: '2026-07-14 11:02:47'
  },

  /* ---------- 2026-08 账期（合计 85.00，2 笔） ---------- */
  {
    id: 5015,
    user_id: 1001,
    type: 'BILL',
    amount: 79.0,
    occur_time: '2026-08-01 00:04:03',
    bill_month: '2026-08',
    remark: '2026 年 8 月套餐月费',
    create_time: '2026-08-01 00:04:03'
  },
  {
    id: 5016,
    user_id: 1001,
    type: 'BILL',
    amount: 6.0,
    occur_time: '2026-08-01 00:06:12',
    bill_month: '2026-08',
    remark: '增值业务-来电显示',
    create_time: '2026-08-01 00:06:12'
  },

  /* ---------- 2026-09 账期（合计 123.60，6 笔） ---------- */
  {
    id: 5017,
    user_id: 1001,
    type: 'BILL',
    amount: 79.0,
    occur_time: '2026-09-01 00:05:12',
    bill_month: '2026-09',
    remark: '2026 年 9 月套餐月费',
    create_time: '2026-09-01 00:05:12'
  },
  {
    id: 5018,
    user_id: 1001,
    type: 'BILL',
    amount: 6.0,
    occur_time: '2026-09-01 00:07:40',
    bill_month: '2026-09',
    remark: '增值业务-来电显示',
    create_time: '2026-09-01 00:07:40'
  },
  {
    id: 5019,
    user_id: 1001,
    type: 'BILL',
    amount: 5.0,
    occur_time: '2026-09-01 00:08:15',
    bill_month: '2026-09',
    remark: '增值业务-彩铃',
    create_time: '2026-09-01 00:08:15'
  },
  {
    id: 5020,
    user_id: 1001,
    type: 'BILL',
    amount: 10.0,
    occur_time: '2026-09-06 10:24:36',
    bill_month: '2026-09',
    remark: '增值业务-和彩云网盘会员',
    create_time: '2026-09-06 10:24:36'
  },
  {
    id: 5021,
    user_id: 1001,
    type: 'BILL',
    amount: 15.6,
    occur_time: '2026-09-15 21:40:02',
    bill_month: '2026-09',
    remark: '国际及港澳台长途通话费',
    create_time: '2026-09-15 21:40:02'
  },
  {
    id: 5022,
    user_id: 1001,
    type: 'BILL',
    amount: 8.0,
    occur_time: '2026-09-20 08:15:47',
    bill_month: '2026-09',
    remark: '增值业务-视频彩铃',
    create_time: '2026-09-20 08:15:47'
  },

  /* ---------- 另一账号（user_id = 1002）的流水 ----------
   *
   * ⚠️ 这两条是**故意混进来的**，不是造数据时手滑。
   *
   * `t_bill` 是全体用户共用的一张表，查询必然带 `WHERE user_id = 当前用户`
   * （真后端从 JWT 里取，不由前端传）。本文件的演示账号是 1001，
   * 所以下面这两条**任何页面都不该显示出来**。
   *
   * 如果只有 1001 一家的数据，「有没有加用户过滤」这件事就测不出来——
   * 漏掉 `WHERE user_id` 会返回全表，而全表里恰好全是这个人的记录，
   * 页面看着完全正常。放一条别人的进来，漏过滤就会立刻穿帮：
   * 账单明细会变成 7 条、缴费记录会多出一条 200 元的充值。
   *
   * 1002 这条特意选了**金额与众不同**的 200.00，方便一眼认出来。
   */
  {
    id: 5901,
    user_id: 1002,
    type: 'PAY',
    amount: 200.0,
    occur_time: '2026-09-10 16:05:00',
    bill_month: null,
    remark: '微信充值',
    create_time: '2026-09-10 16:05:00'
  },
  {
    id: 5902,
    user_id: 1002,
    type: 'BILL',
    amount: 129.0,
    occur_time: '2026-09-01 00:05:30',
    bill_month: '2026-09',
    remark: '2026 年 9 月套餐月费',
    create_time: '2026-09-01 00:05:30'
  }
]
