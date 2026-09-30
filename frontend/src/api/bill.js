import { mockBills } from './mock/bill'
import { mockAccount } from './mock/account'
import { DEMO_MONTH } from './mock/period'
import { paginate } from './mock/paginate'

/**
 * 费用流水查询。《开发文档》§5.2 ④ 给了三个口径，本文件一一对应：
 *
 *   | 口径                    | 用它的页面 |
 *   | ----------------------- | ---------- |
 *   | `type='PAY'`            | 话费查询 · 缴费记录 |
 *   | `type='BILL' GROUP BY bill_month` | 账单查询 · 按月列表 |
 *   | `type='BILL' AND bill_month=?`    | 账单查询 · 该月明细 |
 *
 * 另有第四个用途——账户总览的「最近账单」，它只要最近几条，见 `listRecentBills`。
 *
 * ⚠️ **每个查询都按 `user_id` 过滤**，取的是当前登录账号。
 *    真后端的 `user_id` 来自 JWT，不由前端传；这里用 `mockAccount.id` 顶替，
 *    就是「假装我是那个从 token 里读出来的当前用户」。所以下面几个函数
 *    都**不需要调用方传 userId**——签名与将来的后端接口保持一致。
 *    `mock/bill.js` 里混了一条 1002 的记录，正是为了让这个过滤能被测出来。
 */
const CURRENT_USER_ID = mockAccount.id

/** 只保留当前用户的流水。真后端由 SQL 的 WHERE 完成。 */
function visibleBills() {
  return mockBills.filter((item) => item.user_id === CURRENT_USER_ID)
}

/** 按发生时间倒序。账单页要的都是「新的在前」。 */
function byOccurTimeDesc(a, b) {
  return b.occur_time.localeCompare(a.occur_time)
}

/**
 * 最近账单（消费记录），按发生时间倒序取前 N 条。
 *
 * 账户总览用。它**不分页**——一屏概览只放得下几条，为此挂个分页控件没有意义。
 *
 * 后端就绪后换成：return request.get('/bills/recent', { params: { limit } })
 */
export function listRecentBills(limit = 3) {
  const rows = visibleBills().filter((item) => item.type === 'BILL').sort(byOccurTimeDesc).slice(0, limit)

  return Promise.resolve(rows)
}

/**
 * 话费查询 · 缴费记录（分页）。
 *
 * 缴费记录是只增不减的一串，最需要分页。
 *
 * 后端就绪后换成：
 *     return request.get('/bills/payments', { params: { page, size } })
 */
export function listPayments({ page = 1, size = 5 } = {}) {
  const rows = visibleBills().filter((item) => item.type === 'PAY').sort(byOccurTimeDesc)

  return Promise.resolve(paginate(rows, page, size))
}

/**
 * 账单查询 · 按月列表。
 *
 * 即 `WHERE type='BILL' GROUP BY bill_month`，聚合出每个账期的消费合计与笔数。
 *
 * **这个查询不分页**：账期的数量等于用户入网月数除以 12 再向上取整，本演示数据
 * 只有 5 个月，接后端后一年也只有 12 行。给它套分页控件是纯粹的多余动作。
 * （`size` 传再大也不会退化——真后端可以按年过滤，那是后话。）
 *
 * 后端就绪后换成：return request.get('/bills/months')
 */
export function listBillMonths() {
  const grouped = new Map()

  for (const item of visibleBills()) {
    if (item.type !== 'BILL') continue
    const bucket = grouped.get(item.bill_month) || { bill_month: item.bill_month, cents: 0, count: 0 }
    // ⚠️ 按「分」累加再折回元。MySQL 里 `amount` 是 DECIMAL(10,2)，相加不会有误差；
    //    JS 的 Number 是双精度浮点，直接加会出现 `123.60000000000001` 这种尾巴。
    //    它长得像真数据，摆到页面上很容易被当成显示 bug 一路查下去。
    bucket.cents += Math.round(item.amount * 100)
    bucket.count += 1
    grouped.set(item.bill_month, bucket)
  }

  // 账期是 `YYYY-MM` 定长字符串，字典序即时间序，不必转成日期再比
  const rows = [...grouped.values()]
    .map(({ bill_month, cents, count }) => ({ bill_month, amount: cents / 100, count }))
    .sort((a, b) => b.bill_month.localeCompare(a.bill_month))

  return Promise.resolve(rows)
}

/**
 * 账单查询 · 某个账期的消费明细（分页）。
 *
 * 后端就绪后换成：
 *     return request.get('/bills', { params: { month, page, size } })
 */
export function listBills({ month = DEMO_MONTH, page = 1, size = 5 } = {}) {
  const rows = visibleBills()
    .filter((item) => item.type === 'BILL' && item.bill_month === month)
    .sort(byOccurTimeDesc)

  return Promise.resolve(paginate(rows, page, size))
}
