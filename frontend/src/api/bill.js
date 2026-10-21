import request from './request'
import { DEMO_MONTH } from './mock/period'

/**
 * 费用流水查询。《开发文档》§5.2 ④ 给了三个口径，本文件一一对应：
 *
 *   | 口径                              | 用它的页面       |
 *   | --------------------------------- | ---------------- |
 *   | `type='PAY'`                      | 话费查询 · 缴费记录 |
 *   | `type='BILL' GROUP BY bill_month` | 账单查询 · 按月列表 |
 *   | `type='BILL' AND bill_month=?`    | 账单查询 · 该月明细 |
 *
 * 另有第四个用途——账户总览的「最近账单」，见 `listRecentBills`。
 *
 * ⚠️ **`user_id` 由后端从 JWT 里取，不由前端传**。所以这几个函数的签名里
 *    没有 userId，页面调用时也不必知道「我是谁」。接口上多一个 userId 参数，
 *    等于允许任何人查任何人的账单。
 */
const PAGE_PARAMS = (page, size) => ({ page, size })

/**
 * 最近账单（消费记录），按发生时间倒序取前 N 条。
 *
 * 账户总览用。它**不分页**——一屏概览只放得下几条，为此挂个分页控件没有意义。
 */
export function listRecentBills(limit = 3) {
  return request.get('/bills/recent', { params: { limit } })
}

/** 话费查询 · 缴费记录（分页）。 */
export function listPayments({ page = 1, size = 5 } = {}) {
  return request.get('/bills/payments', { params: PAGE_PARAMS(page, size) })
}

/**
 * 账单查询 · 按月列表。
 *
 * 即 `WHERE type='BILL' GROUP BY bill_month`，聚合出每个账期的消费合计与笔数。
 * 后端一次 SQL 算完（金额在 DECIMAL 上累加，不会有浮点尾巴）。
 *
 * **不分页**：账期的数量等于用户入网月数除以 12 再向上取整，一年最多 12 行。
 */
export function listBillMonths() {
  return request.get('/bills/months')
}

/**
 * 账单查询 · 某个账期的消费明细（分页）。
 *
 * `month` 默认取演示账期（见 `mock/period.js` 顶部：种子数据围绕 2026-09 构造）。
 * 演示期结束后删掉这个默认值，让服务端按当前时间给账期。
 */
export function listBills({ month = DEMO_MONTH, page = 1, size = 5 } = {}) {
  return request.get('/bills', { params: { month, ...PAGE_PARAMS(page, size) } })
}
