/**
 * 工单处理人参照表。
 *
 * 真后端里「处理人」不是一张独立的表——`t_ticket.handler_id` 就是
 * `t_account.id`，查询时 JOIN 一下拿 `login_name`。前端接后端后，
 * `api/admin.js` 直接读后端已经拼好的 `handler_name`，**本文件随 mock 目录删除**。
 *
 * ⚠️ **这张小表不是多余的，不能删掉改用 `mockAccounts` 解析。**
 *    既有 `mockTickets` 里历史工单的 `handler_id` 是 **2**（见 `mock/ticket.js`
 *    的 7003–7006、7902），而 id 2 **不在** `mockAccounts` 里——那张表的 id 是
 *    1001 起的用户号段与 2001 / 3001 的角色号段。若只按 `mockAccounts` 解析，
 *    五条历史工单的「处理人」列会**全部**显示成「未知」，看起来像解析逻辑写错了，
 *    实际是这张表缺了 id 2 这一行。
 *
 * ⚠️ id 2 的名字（客服 · 李敏）**不是**从别处推导出来的，是这里新起的：
 *    历史工单需要一个「已具名的处理人」，而种子数据里只有 id、没有名字。
 *    取一个自然人名字而不是「客服1」，是为了让「处理人」列在演示时读起来
 *    像真的——但它是**凭空造的**，不要试图去别处找它的出处。
 *
 * id 3001 是 `mockAdminAccount.id`，管理员在管理后台点「开始处理」时
 * `handler_id` 就填它（顶替「JWT 里的管理员」），所以两条要同时存在。
 */
export const mockHandlers = [
  { id: 2, name: '客服 · 李敏' },
  { id: 3001, name: '系统管理员' }
]

/** 按 id 解析处理人显示名。取不到返回空串，由调用方决定显示成什么。 */
export function resolveHandlerName(handlerId) {
  if (handlerId === null || handlerId === undefined) return ''
  return mockHandlers.find((h) => h.id === handlerId)?.name || '未知处理人'
}
