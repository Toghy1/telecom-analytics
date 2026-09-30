import { mockTickets } from './mock/ticket'
import { mockAccount } from './mock/account'
import { paginate } from './mock/paginate'
import { TICKET_CONTENT_LIMIT, TICKET_TYPE } from '@/utils/constants'
import { nowText } from '@/utils/datetime'

/**
 * 工单提交与查询（《开发文档》§6.4 #6）。
 *
 * ⚠️ **每个查询都按 `user_id` 过滤**，取当前登录账号。真后端的 `user_id` 来自
 *    JWT，不由前端传；这里用 `mockAccount.id` 顶替，就是「假装我是那个从 token
 *    里读出来的当前用户」。所以下面几个函数都**不需要调用方传 userId**，
 *    签名与将来的后端接口保持一致。
 *    `mock/ticket.js` 里混了两条 1002 的记录，正是为了让这个过滤能被测出来。
 */
const CURRENT_USER_ID = mockAccount.id

/** 按提交时间倒序——「我的工单」要的是「新的在前」。
 *  `create_time` 是 `YYYY-MM-DD HH:mm:ss` 定宽格式，字典序即时间序。 */
function byCreateTimeDesc(a, b) {
  return b.create_time.localeCompare(a.create_time)
}

/**
 * 我的工单（分页）。
 *
 * 后端就绪后换成：
 *     return request.get('/tickets', { params: { page, size } })
 */
export function listTickets({ page = 1, size = 5 } = {}) {
  const rows = mockTickets
    .filter((item) => item.user_id === CURRENT_USER_ID)
    .sort(byCreateTimeDesc)

  return Promise.resolve(paginate(rows, page, size))
}

/**
 * 提交工单。
 *
 * ⚠️ 新工单的 `status` 恒为 `PENDING`。**状态不是前端能挑的**——流转
 *    （待处理 → 处理中 → 已完成）由管理后台驱动，是本项目两处真实业务逻辑之一。
 *    让调用方传 status 等于把状态机的入口敞开，谁都能塞一个「已完成」进来。
 *
 * ⚠️ `handler_id` / `handle_time` 留空——刚提交的工单还没人接手。
 *    这两项由管理后台在处理时填，与 `mock/ticket.js` 里那条不变量一致。
 *
 * 后端就绪后，整个函数体换成：
 *     return request.post('/tickets', { type, content })
 */
export function submitTicket({ type, content }) {
  if (!TICKET_TYPE[type]) {
    return Promise.reject(new Error('请选择报障类型'))
  }

  const text = String(content ?? '').trim()
  if (text.length < TICKET_CONTENT_LIMIT.min) {
    return Promise.reject(new Error(`问题描述至少 ${TICKET_CONTENT_LIMIT.min} 个字`))
  }
  // 表单已用 `maxlength` 拦过一道，这里再拦一次：api 层不能假设调用方一定是表单。
  if (text.length > TICKET_CONTENT_LIMIT.max) {
    return Promise.reject(new Error(`问题描述不能超过 ${TICKET_CONTENT_LIMIT.max} 个字`))
  }

  const ticket = {
    // 取号 = **全局**最大 id + 1，扫描范围刻意包含其他用户的记录。
    // 真库的 AUTO_INCREMENT 就是全局递增、不按用户分段，所以新单号可能
    // 一下子跳到 79xx（因为 mock 里混了 1002 的 7901 / 7902）。
    // 看起来跳号，但这才是真实行为——按当前用户取 max 反而会和真库对不上。
    id: mockTickets.reduce((max, item) => Math.max(max, item.id), 0) + 1,
    user_id: CURRENT_USER_ID,
    type,
    content: text,
    status: 'PENDING',
    handler_id: null,
    create_time: nowText(),
    handle_time: null
  }

  // ⚠️ 这一行是**假数据阶段对「真后端写库」的模拟**，接后端时整段删除。
  //    改的是 mock 模块里的数组，刷新页面即还原（等于数据库回到种子数据）。
  mockTickets.push(ticket)

  return Promise.resolve({ ...ticket })
}
