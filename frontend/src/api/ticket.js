import request from './request'

/**
 * 工单提交与查询（《开发文档》§6.4 #6）。
 *
 * ⚠️ **`user_id` 由后端从 JWT 里取，不由前端传**。所以函数签名里没有 userId。
 */

/** 我的工单（分页），按提交时间倒序。 */
export function listTickets({ page = 1, size = 5 } = {}) {
  return request.get('/tickets', { params: { page, size } })
}

/**
 * 提交工单。入参 `{type, content}`。
 *
 * ⚠️ 新工单的 `status` 恒为 `PENDING`，由**后端**写死。状态不是前端能挑的
 *    ——流转（待处理 → 处理中 → 已完成）由管理后台驱动，是本项目两处真实
 *    业务逻辑之一。让调用方传 status 等于把状态机的入口敞开。
 *    `handler_id` / `handle_time` 同理：刚提交的工单还没人接手。
 *
 * 类型（`TICKET_TYPE` 四选一）与字数（trim 后 5~200）的校验在后端，
 * 错误文案与 `utils/constants.js` 的口径一致，页面直接把 `message` 弹出来即可。
 *
 * 返回新建的那条记录（含自增 `id`），页面据此刷新列表。
 */
export function submitTicket({ type, content }) {
  return request.post('/tickets', { type, content })
}
