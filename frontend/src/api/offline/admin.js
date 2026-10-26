import { mockAccounts, mockAdminAccount, toMaskedLoginName, toMaskedIdCard } from '@/api/mock/account'
import { mockPackages } from '@/api/mock/package'
import { mockTickets } from '@/api/mock/ticket'
import { mockOrders } from '@/api/mock/order'
import { mockBills } from '@/api/mock/bill'
import { resolveHandlerName } from '@/api/mock/handler'
import { paginate } from '@/api/mock/paginate'
import { ROLE_LABEL } from '@/utils/constants'
import { nowText } from '@/utils/datetime'

/**
 * 管理后台（`/admin/*`）接口。《开发文档》§6.6。
 *
 * ⚠️ **本文件存在的理由，是下面三个查询函数的语义与既有同名函数正好相反。**
 *    本项目每张业务表都被多个角色查询，而「加不加 WHERE」是两回事：
 *
 *      `api/package.js#listPackages`   过滤 `status === 1`  → 管理端必须不过滤
 *      `api/ticket.js#listTickets`     过滤 `user_id`       → 管理端必须不过滤
 *
 *    这两种语义**不能合并进同一个函数**，所以本文件独立存在，而不是往
 *    `api/ticket.js` 里加一个 `if (isAdmin)`——那种写法让「管理员看到的到底是
 *    全部还是自己那一份」变成一个躲在函数体里的运行时分支，读签名看不出来。
 *    分开写的话，调用方一眼就知道自己拿到的是哪个口径。
 *
 * 本阶段是「纯前端 + 假数据」：写操作**真的改 mock 数组**，模拟真后端写库，
 * 刷新页面即还原。接后端时逐函数换成 `request.get/post/put/delete`，
 * **页面一行都不用改**（选型⑤纪律①）。
 */

/* =========================================================================
 * 用户管理
 * ========================================================================= */

/**
 * 账号列表（分页 + 筛选）。
 *
 * 后端就绪后换成：
 *     return request.get('/admin/accounts', { params: { page, size, role, status, keyword } })
 *
 * ⚠️ 筛选**在这里真过滤**，不在页面 `filter()`。留到页面做的话，分页数字
 *    （`total` / `pages`）算的还是过滤前的量，表现为「筛完只剩 3 条却显示共 16 条、
 *    还能翻到第 4 页」。接后端时这些条件会变成 `WHERE`，口径必须与现在一致。
 *
 * ⚠️ `status` 的合法取值**包含 `0`**（停用），所以判空必须写
 *    `status === undefined || status === ''`，**不能写 `if (status)`**——
 *    后者对 `0` 为假，会把「停用」这一档整个丢掉，且**不报错**：
 *    页面上就是筛出来一条都没有，看着像「确实没有停用账号」。
 *
 * @param {string} [role]    角色编码，空/不传 = 全部
 * @param {number|string} [status] 1 正常 / 0 停用，空/不传 = 全部
 * @param {string} [keyword] 模糊匹配脱敏后的 `login_name` 与 `region`
 */
export function listAccounts({ page = 1, size = 5, role, status, keyword } = {}) {
  const k = String(keyword ?? '').trim()

  const rows = mockAccounts
    .filter((a) => role === undefined || role === '' || a.role === role)
    .filter((a) => status === undefined || status === '' || a.status === status)
    .filter((a) => !k || a.login_name.includes(k) || (a.region || '').includes(k))
    // 按 id 升序：管理端的账号列表读起来要像一本名册，顺序稳定可预期。
    // 不按 create_time 排——两个角色账号与演示账号的 create_time 同日，
    // 按时间排会让它们在页与页之间飘。
    .slice()
    .sort((a, b) => a.id - b.id)
    .map((a) => ({ ...a, package_name: resolvePackageName(a.package_id) }))

  return Promise.resolve(paginate(rows, page, size))
}

/**
 * 新增账号。
 *
 * ⚠️ **明文到这里为止，不落数组。** 调用方传进来的是用户在表单里填的**明文手机号**，
 *    本函数用 `toMaskedLoginName` 脱敏后才写进 `mockAccounts`——那两个函数在
 *    `mock/account.js` 里，扮演的是**真后端 Entity → VO 那一环**，不是前端脱敏。
 *    接后端时整个 `api/mock/` 目录删除，明文随请求体交给真后端脱敏。
 *
 * ⚠️ `role !== 'USER'` 时**刻意不写 `id_card` / `package_id` / `balance`**，
 *    与 `mockAnalystAccount` 同形（角色表的「最小必要」：分析员与管理员
 *    根本没有套餐、余额、身份证这些字段）。真实后端也不会为这两个角色返回它们。
 */
export function createAccount({ login_name, role, region, id_card, package_id, balance }) {
  const raw = String(login_name ?? '').trim()

  if (!ROLE_LABEL[role]) {
    return Promise.reject(new Error('请选择账号角色'))
  }
  if (!raw) {
    return Promise.reject(new Error('请填写账号'))
  }
  // 电信用户以手机号入网（《开发文档》§5.2 ①：用户填手机号，管理员填用户名）。
  // 拦在这里而不是只靠表单校验：api 层不能假设调用方一定是表单。
  if (role === 'USER' && !/^1\d{10}$/.test(raw)) {
    return Promise.reject(new Error('电信用户请填写 11 位手机号'))
  }

  const maskedLoginName = toMaskedLoginName(raw)
  if (mockAccounts.some((a) => a.login_name === maskedLoginName)) {
    // ⚠️ 比的是**脱敏后**的名字——真后端的唯一索引落在明文列上，但用户名
    //    相同必然脱敏结果也相同，所以这条检查在假数据阶段是等价的。
    return Promise.reject(new Error('该账号已存在'))
  }

  const rawIdCard = String(id_card ?? '').trim()
  // 身份证可留空（真实入网也允许暂不补录），但**填了就必须是 18 位**——
  // 否则它会被 `toMaskedIdCard` 原样返回、明文落进列表，而脱敏那条红线
  // 要的恰恰是「列表里永远不会出现明文身份证」。
  if (rawIdCard && !/^\d{18}$/.test(rawIdCard)) {
    return Promise.reject(new Error('身份证号应为 18 位'))
  }

  const row = {
    // 取号 = 全局最大 id + 1，与 `submitTicket` 同法（真库 AUTO_INCREMENT 就是全局递增）。
    id: mockAccounts.reduce((max, a) => Math.max(max, a.id), 0) + 1,
    login_name: maskedLoginName, // ← 明文不落数组，写进去的已是脱敏态
    role,
    region: String(region ?? '').trim(),
    status: 1, // 新建的账号一律正常，要停用走「停用」按钮
    create_time: nowText(),
    ...(role === 'USER'
      ? {
          id_card: toMaskedIdCard(rawIdCard),
          package_id: package_id ?? null,
          balance: Number(balance) || 0
        }
      : {})
  }

  mockAccounts.push(row) // 假数据阶段对「真后端写库」的模拟，接后端时整段删除
  return Promise.resolve({ ...row })
}

/**
 * 启用 / 停用账号。
 *
 * ⚠️ 拒绝停用当前登录的管理员自己。理由不是「怕你锁死自己」——真实系统里
 *    你还能用另一个管理员账号登进来。理由是这条规则和 `deleteAccount` 的
 *    自锁检查是**同一条业务纪律**（「不能把当前操作者自己搞掉」），
 *    两处措辞一致，演示时讲一次就够。
 *
 * 后端就绪后换成：return request.put(`/admin/accounts/${id}/status`, { status })
 */
export function updateAccountStatus({ id, status }) {
  const row = mockAccounts.find((a) => a.id === id)
  if (!row) {
    return Promise.reject(new Error('账号不存在'))
  }
  if (id === mockAdminAccount.id) {
    return Promise.reject(new Error('不能停用当前登录的账号'))
  }
  if (status !== 0 && status !== 1) {
    return Promise.reject(new Error('状态取值非法'))
  }

  row.status = status
  return Promise.resolve({ ...row })
}

/**
 * 重置密码为初始口令。
 *
 * ⚠️ **假数据阶段这是「空操作」，只有一句成功提示——但这不是偷懒。**
 *    真实的 `t_account.password` 存的是 BCrypt 哈希，重置就是后端把哈希算一遍
 *    写回去，返回给前端的仍然只有「成功」两个字。前端**本来就没有**可改的字段，
 *    所以这里不返回任何数据、也不动 `DEMO_ACCOUNTS`，与真实流程的形状一致。
 *
 *    副作用是：重置后用**原口令仍能登录**。这是假数据阶段的已知落差，
 *    不是 bug——`DEMO_ACCOUNTS` 是登录比对用的明文表，与密码重置是两回事。
 *
 * 后端就绪后换成：return request.put(`/admin/accounts/${id}/password/reset`)
 */
export function resetAccountPassword({ id }) {
  const row = mockAccounts.find((a) => a.id === id)
  if (!row) {
    return Promise.reject(new Error('账号不存在'))
  }
  return Promise.resolve(true)
}

/**
 * 删除账号。
 *
 * ⚠️ 两道守卫，都在模拟真后端的外键约束（`t_bill` / `t_order` / `t_ticket`
 *    的 `user_id` 都指向 `t_account.id`，真库删被引用的行会被拒绝）：
 *
 *   ① 不能删当前登录的管理员自己（自锁）。
 *   ② 有业务数据的账号不能删，改用「停用」。
 *
 *    种子账号 1001 / 1002 必然被②拒绝（它们有账单、办理、工单），这是**常态**，
 *    不是守卫写死了。演示「删除成功」的正向路径是：**新建一个账号，再删掉它**。
 *
 * 后端就绪后换成：return request.delete(`/admin/accounts/${id}`)
 */
export function deleteAccount(id) {
  const index = mockAccounts.findIndex((a) => a.id === id)
  if (index === -1) {
    return Promise.reject(new Error('账号不存在'))
  }
  if (id === mockAdminAccount.id) {
    return Promise.reject(new Error('不能删除当前登录的账号'))
  }

  const used =
    mockBills.some((b) => b.user_id === id) ||
    mockOrders.some((o) => o.user_id === id) ||
    mockTickets.some((t) => t.user_id === id)

  if (used) {
    return Promise.reject(
      new Error('该账号存在业务数据（账单 / 办理 / 工单），无法删除，请改用「停用」')
    )
  }

  mockAccounts.splice(index, 1)
  return Promise.resolve(true)
}

/* =========================================================================
 * 套餐配置
 * ========================================================================= */

/**
 * 套餐列表（分页 + 筛选）——**这是管理端，与营业厅的口径不同**。
 *
 * ⚠️ **不过滤 `status`。** 与 `api/package.js#listPackages` 恰好相反：
 *    那个函数回答「现在能办哪些」（`status === 1`），本函数回答「系统里有哪些」。
 *    图省事复用 `listPackages()` 的话，已停售的「学生特惠 29 元套餐」会
 *    从管理端列表里**凭空消失且不报错**——而它正是管理端最该看见的那一条
 *    （用户要下架它就得先找得到它）。
 *
 * ⚠️ 同样，解析套餐名不能用 `listPackages()`：历史办理记录里引用的套餐
 *    可能已停售。理由见 `api/order.js#resolvePackage`。
 *
 * @param {number|string} [status] 1 在售 / 0 停售，空/不传 = 全部（同上，不能写 `if (status)`）
 */
export function listAdminPackages({ page = 1, size = 5, status, keyword } = {}) {
  const k = String(keyword ?? '').trim()

  const rows = mockPackages
    .filter((p) => status === undefined || status === '' || p.status === status)
    .filter((p) => !k || p.name.includes(k))
    .slice()
    .sort((a, b) => a.monthly_fee - b.monthly_fee) // 与营业厅同一排序：按月费升序
    .map((p) => ({ ...p, user_count: countPackageUsers(p.id) }))

  return Promise.resolve(paginate(rows, page, size))
}

/**
 * 套餐下拉选项（`{ value, label }`），供「新增账号」挑当前套餐用。
 *
 * ⚠️ 单独开一个函数，而不是让页面调 `listAdminPackages({ size: 100 })` 再取
 *    `records`——那种写法要页面自己知道「size 传多大才够」，套餐一多就悄悄截断，
 *    而且它把「下拉选项」和「分页列表」这两件事混成一件。
 *
 * ⚠️ **不过滤 `status`**：给存量用户指派套餐时，已停售的套餐仍要能选
 *    （账号 1006 / 1013 当前用的就是那条已停售的学生特惠）。选项文案里带出
 *    状态，让「为什么这里有个停售的」自解释。
 */
export function listPackageOptions() {
  const options = mockPackages
    .slice()
    .sort((a, b) => a.monthly_fee - b.monthly_fee)
    .map((p) => ({ value: p.id, label: `${p.name}（${p.monthly_fee} 元${p.status === 1 ? '' : ' · 已停售'}）` }))

  return Promise.resolve(options)
}

/**
 * 新增套餐。
 *
 * 后端就绪后换成：return request.post('/admin/packages', payload)
 */
export function createPackage({ name, monthly_fee, data_quota, voice_quota, sms_quota, description, status }) {
  const checked = validatePackageFields({ name, monthly_fee, data_quota, voice_quota, sms_quota })
  if (checked.error) {
    return Promise.reject(new Error(checked.error))
  }

  const row = {
    id: mockPackages.reduce((max, p) => Math.max(max, p.id), 0) + 1,
    ...checked.value,
    description: String(description ?? '').trim(),
    status: status === 0 ? 0 : 1,
    create_time: nowText()
  }

  mockPackages.push(row)
  return Promise.resolve({ ...row })
}

/**
 * 编辑套餐。
 *
 * ⚠️ `id` 与 `create_time` **不随编辑改动**——`create_time` 是「这个套餐什么时候
 *    上架的」，编辑文案不该改它。改动它会让 `mock/order.js` 里那条
 *    「上架时间早于办理时间」的时间线自洽性断掉。
 *
 * 后端就绪后换成：return request.put(`/admin/packages/${id}`, patch)
 */
export function updatePackage({ id, name, monthly_fee, data_quota, voice_quota, sms_quota, description, status }) {
  const row = mockPackages.find((p) => p.id === id)
  if (!row) {
    return Promise.reject(new Error('套餐不存在'))
  }

  const checked = validatePackageFields({ name, monthly_fee, data_quota, voice_quota, sms_quota })
  if (checked.error) {
    return Promise.reject(new Error(checked.error))
  }

  Object.assign(row, checked.value, {
    description: String(description ?? '').trim(),
    status: status === 0 ? 0 : 1
  })

  return Promise.resolve({ ...row })
}

/**
 * 删除套餐。
 *
 * ⚠️ **有引用就拒绝，改用「停售」。** 这三条理由一条比一条硬：
 *
 *   ① 真库的 `t_account.package_id` / `t_order.package_id` 是外键，删被引用的行
 *      本就会被数据库拒绝。这里是**模拟那个约束**，与 `submitOrder` 用 reject
 *      模拟业务校验同法。
 *   ② 与既有那对「`listPackages` 过滤 status、`getPackageById` 不过滤」的设计咬合：
 *      **停售**只让套餐从营业厅消失，历史记录仍能解析出名字；**删除**会让
 *      `api/order.js#resolvePackage` 找不到套餐，历史办理记录显示成「未知套餐」
 *      ——一个不报错的静默数据错误。
 *   ③ 种子数据里套餐 1–5 **全部**被引用（5 被 `mock/order.js` 的历史记录引用，
 *      2 是 `mockAccount.package_id`），所以「删不掉」是常态。正向路径同样是
 *      「新建 → 删除」。
 *
 * 后端就绪后换成：return request.delete(`/admin/packages/${id}`)
 */
export function deletePackage(id) {
  const index = mockPackages.findIndex((p) => p.id === id)
  if (index === -1) {
    return Promise.reject(new Error('套餐不存在'))
  }

  const used = countPackageUsers(id)
  if (used > 0) {
    return Promise.reject(
      new Error(`该套餐已被使用（关联 ${used} 条记录），无法删除；如需对用户下架，请改用「停售」`)
    )
  }

  mockPackages.splice(index, 1)
  return Promise.resolve(true)
}

/* =========================================================================
 * 工单管理
 * ========================================================================= */

/**
 * 全部工单（分页 + 筛选）——**不按 `user_id` 过滤**。
 *
 * ⚠️ 与 `api/ticket.js#listTickets` 恰好相反，且**不能复用它**：那个函数硬过滤了
 *    `CURRENT_USER_ID`。管理员复用它的话，列表里只会出现 1001 的工单，而
 *    `mock/ticket.js` 里那两条 1002 的记录（7901 / 7902）**其中一条的 create_time
 *    取的是全局最新**——按时间倒序它本该是第一行，少掉它就能一眼看见。
 *    真后端同理：JWT 里是管理员，不加 `WHERE user_id`。
 *
 * ⚠️ `status` 判空同上，`PENDING` / `PROCESSING` / `DONE` 都是非空字符串，
 *    这里没有 `0` 的坑，但保持与另两个列表同一写法以免日后加状态时踩。
 *
 * @param {string} [type]   `NETWORK` / `BILLING` / `CONSULT` / `COMPLAINT`
 * @param {string} [status] `PENDING` / `PROCESSING` / `DONE`
 * @param {string} [keyword] 匹配工单号或描述正文
 */
export function listAllTickets({ page = 1, size = 5, type, status, keyword } = {}) {
  const k = String(keyword ?? '').trim()

  const rows = mockTickets
    .filter((t) => type === undefined || type === '' || t.type === type)
    .filter((t) => status === undefined || status === '' || t.status === status)
    .filter((t) => !k || String(t.id).includes(k) || t.content.includes(k))
    .slice() // ⚠️ 先复制再 sort——`sort` 是原地排序，直接排会打乱 mockTickets 的顺序
    .sort((a, b) => b.create_time.localeCompare(a.create_time)) // 新的在前，定宽格式字典序即时间序
    .map((t) => ({
      ...t,
      // 展示所需的两个名字在这里拼好，页面不再查表——接后端时这两行变成 JOIN
      user_login_name: mockAccounts.find((a) => a.id === t.user_id)?.login_name || '未知用户',
      handler_name: resolveHandlerName(t.handler_id)
    }))

  return Promise.resolve(paginate(rows, page, size))
}

/**
 * 工单状态流转 —— 本项目**两处真实业务逻辑之二**（另一处是套餐办理）。
 *
 * ⚠️ 《开发文档》§6.6 明确警告：「不可从简到只剩一个下拉框改值」。
 *    本函数的合法性校验就是那句话里的「要在后端校验」——这里扮演后端。
 *    配套的另一半在 `views/admin/Tickets.vue`：UI **只渲染当前状态的合法动作**
 *    （`DONE` 行没有任何动作按钮），让非法迁移在界面上根本不可达。
 *    两层是**互补而非重复**：UI 那层挡的是「管理员点错」，这层挡的是
 *    「绕过 UI 的调用」和「两个标签页都在操作同一张工单」。
 *
 * 合法迁移只有两条：`PENDING → PROCESSING → DONE`。`DONE` 是终态，无后继。
 *
 * ⚠️ `status` / `handler_id` / `handle_time` 三者必须**同步**更新，遵守
 *    `mock/ticket.js` 头部那三条不变量。特别地：`handle_time` 是
 *    **处理完成**时间，所以「开始处理」时它仍为 `null`——把它当成
 *    「开始处理时间」填上前，会出现「已完成的工单没有完成时间」这种
 *    谁也不会注意到的数据错误。
 *
 * 后端就绪后换成：return request.put(`/admin/tickets/${id}/status`, { status })
 */
export function advanceTicket({ id, status }) {
  const ticket = mockTickets.find((t) => t.id === id)
  if (!ticket) {
    return Promise.reject(new Error('工单不存在'))
  }

  const allowed = TICKET_TRANSITION[ticket.status]
  if (!allowed) {
    // 当前状态没有后继 = 已办结
    return Promise.reject(new Error('该工单已办结，不能再次流转'))
  }
  if (allowed !== status) {
    return Promise.reject(new Error('不允许的状态流转，请刷新后重试'))
  }

  ticket.status = status
  // 顶替「从 JWT 里读出来的管理员」。`??` 而非 `=`：已有人接手过的工单
  // 不该在流转时把处理人改掉（真后端同理，接手人只在第一次落库）。
  ticket.handler_id = ticket.handler_id ?? mockAdminAccount.id

  if (status === 'PROCESSING') {
    ticket.handle_time = null // 「开始处理」≠「已办结」
  } else {
    const t = nowText()
    if (t < ticket.create_time) {
      return Promise.reject(new Error('处理时间不能早于提交时间'))
    }
    ticket.handle_time = t
  }

  return Promise.resolve({ ...ticket })
}

/* =========================================================================
 * 内部工具
 * ========================================================================= */

/**
 * 状态机的合法迁移表。**状态机只有这一处定义**——UI 的动作映射
 * （`Tickets.vue` 的 `TICKET_ACTION`）是从这里推导的展示层，不是第二份真相。
 */
const TICKET_TRANSITION = {
  PENDING: 'PROCESSING',
  PROCESSING: 'DONE'
  // DONE 刻意不出现在表里：`TICKET_TRANSITION['DONE']` 为 undefined，
  // 上面那个 `if (!allowed)` 就是「已办结」的判断，不需要额外写一次 DONE。
}

/**
 * 按 id 解析套餐名。
 *
 * ⚠️ 用 `mockPackages.find` 而**不是** `listPackages()`：后者按 `status === 1`
 *    过滤，历史引用里那些已停售的套餐会解析成空。与 `api/order.js#resolvePackage`
 *    是同一个坑、同一个解法。
 */
function resolvePackageName(packageId) {
  if (packageId === null || packageId === undefined) return ''
  return mockPackages.find((p) => p.id === packageId)?.name || ''
}

/** 某套餐被多少处引用：账号当前套餐 + 历史办理记录。删除守卫用它。 */
function countPackageUsers(packageId) {
  const asCurrent = mockAccounts.filter((a) => a.package_id === packageId).length
  const inOrders = mockOrders.filter((o) => o.package_id === packageId).length
  return asCurrent + inOrders
}

/**
 * 套餐字段校验，新增与编辑共用。
 *
 * 返回 `{ value }` 或 `{ error }` 而不是直接 reject——因为调用方一个是
 * `createPackage` 一个是 `updatePackage`，两边都要用同一套规则。
 * 分开写两份的话，改动时漏一处就会出现「新增拦得住、编辑拦不住」。
 */
function validatePackageFields({ name, monthly_fee, data_quota, voice_quota, sms_quota }) {
  const trimmed = String(name ?? '').trim()
  if (!trimmed) {
    return { error: '请填写套餐名称' }
  }

  const numbers = { monthly_fee, data_quota, voice_quota, sms_quota }
  const labels = { monthly_fee: '月费', data_quota: '流量', voice_quota: '通话', sms_quota: '短信' }

  for (const key of Object.keys(numbers)) {
    const raw = numbers[key]
    // 空值单独判：`Number('') === 0`，不判的话「月费没填」会被当成 0 元放过去。
    if (raw === '' || raw === null || raw === undefined) {
      return { error: `请填写${labels[key]}` }
    }
    const n = Number(raw)
    if (!Number.isFinite(n) || n < 0) {
      return { error: `${labels[key]}不能为负数` }
    }
    numbers[key] = n
  }

  return { value: { name: trimmed, ...numbers } }
}
