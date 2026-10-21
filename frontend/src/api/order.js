import request from './request'

/**
 * 套餐办理与办理记录查询（《开发文档》§6.4 #5）。
 *
 * ⚠️ **`user_id` 由后端从 JWT 里取，不由前端传**。所以函数签名里没有 userId。
 *
 * 语义按 2026-09-30 决策「套餐办理即时生效」：
 *   - `t_account.package_id` **立即改**；
 *   - `t_order.status` 固定为 `1`（已生效），不做状态流转；
 *   - **本月不另收月费**——当月账单里已有一笔月费，不再收第二笔。
 *
 * 连带后果（刻意的，不是 bug）：降档时新套餐额度可能低于本月已用量，
 * 余量会显示为负数并走红色超套样式。办理页在降档前会弹二次确认把这件事说明白。
 */

/**
 * 办理记录（分页）。
 *
 * 返回的每条记录都带 `package_name / monthly_fee`：后端解析时用的是
 * 「按 id 单查」，**不是**「在售套餐列表」——后者按 `status = 1` 过滤，
 * 会把历史记录里已下架的套餐解析成空白，且不报错。
 */
export function listOrders({ page = 1, size = 5 } = {}) {
  return request.get('/orders', { params: { page, size } })
}

/**
 * 办理 / 变更套餐。
 *
 * 入参 `{package_id}`。后端的校验顺序与假数据阶段一致：
 *   套餐不存在 → 「该套餐不存在」
 *   已下架     → 「该套餐已下架，无法办理」
 *   与当前相同 → 「当前已是该套餐，无需重复办理」
 *
 * 返回 `{package_id, package_name, order_time}`。写库是**两张表**（账号当前套餐 +
 * 办理记录），后端用同一个事务包住——两步只成一步会留下「办过但没记录」的状态。
 */
export function submitOrder({ package_id }) {
  return request.post('/orders', { package_id })
}
