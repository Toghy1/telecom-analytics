import request from './request'

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
 * **2026-10-21 接线**：三个页面已全部走 `backend/business` 的 `/api/admin/**`。
 *    假数据阶段写在浏览器里的那些规则——删除守卫、自锁检查、工单状态机、
 *    明文脱敏——现在都在服务端执行，绕过页面直接调接口也绕不过去。
 *    **页面组件一行未改**（选型⑤纪律①）。
 *
 * ⚠️ 脱敏仍由后端完成（选型⑥）：列表里的 `login_name` / `id_card` 拿到的
 *    本身就是脱敏态，本文件不做任何星号拼接。唯一例外是 `createAccount`
 *    的**入参**：那是管理员刚在表单里填的明文手机号，随请求体交给后端，
 *    后端入库明文、出参脱敏——与真实系统的流程完全一致。
 */

/* =========================================================================
 * 用户管理
 * ========================================================================= */

/**
 * 账号列表（分页 + 筛选）。
 *
 * ⚠️ 筛选在**后端 SQL** 里做，不在页面 `filter()`。留到页面做的话，分页数字
 *    （`total` / `pages`）算的还是过滤前的量，表现为「筛完只剩 3 条却显示共
 *    502 条、还能翻到第 100 页」。
 *
 * ⚠️ `status` 的合法取值**包含 `0`**（停用），后端按 `null` 判「不筛」。
 *    前端把「全部」传成空串，Spring 会转成 null；不能改成 `status || undefined`
 *    那种写法——它对 `0` 为假，会把「停用」这一档整个丢掉，且不报错。
 *
 * ⚠️ `keyword` 匹配的是**库里的明文账号**与归属地。管理员输入手机号片段
 *    （如 `1234`）能命中；输入列表里显示的那串 `138****1234` 不会命中——
 *    星号只是展示态，不是库里的值。
 */
export function listAccounts({ page = 1, size = 5, role, status, keyword } = {}) {
  return request.get('/admin/accounts', { params: { page, size, role, status, keyword } })
}

/**
 * 新增账号。
 *
 * `login_name` 是明文手机号（电信用户）或用户名（分析员 / 管理员）；
 * 11 位手机号、身份证 18 位、重复账号、角色合法性全部由后端校验，
 * 文案与假数据阶段逐字一致。非 USER 角色即使传了 `id_card` / `package_id` /
 * `balance`，后端也会忽略——角色表定的是「最小必要」。
 *
 * 初始密码由后端固定为 `123456`（演示约定），不随响应返回。
 */
export function createAccount({ login_name, role, region, id_card, package_id, balance }) {
  return request.post('/admin/accounts', {
    login_name,
    role,
    region,
    id_card,
    package_id,
    balance
  })
}

/**
 * 启用 / 停用账号。
 *
 * ⚠️ 不能停用**当前登录的账号自己**——这条守卫在后端
 * （`AdminAccountService#updateStatus`），页面灰掉按钮只是不制造一个能点的陷阱。
 */
export function updateAccountStatus({ id, status }) {
  return request.put(`/admin/accounts/${id}/status`, { status })
}

/**
 * 重置密码为初始口令 `123456`。
 *
 * 后端把新的 BCrypt 哈希写回 `t_account.password`；**出参只有「成功」**，
 * 前端没有任何可改的字段。真实系统同样不会把口令回传给浏览器。
 */
export function resetAccountPassword({ id }) {
  return request.put(`/admin/accounts/${id}/password/reset`)
}

/**
 * 删除账号。
 *
 * ⚠️ 两道守卫都在后端：① 不能删当前登录的自己；② 有账单 / 办理 / 工单
 *    数据的账号不能删（`t_bill` / `t_order` / `t_ticket` 的 `user_id` 都指向它），
 *    提示改用「停用」。种子账号 1001 / 1002 必然被②拒绝，这是**常态**；
 *    演示「删除成功」的正向路径是：**新建一个账号，再删掉它**。
 */
export function deleteAccount(id) {
  return request.delete(`/admin/accounts/${id}`)
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
 * 每行带 `user_count`（账号当前套餐 + 历史办理记录），用来解释「为什么删不掉」。
 */
export function listAdminPackages({ page = 1, size = 5, status, keyword } = {}) {
  return request.get('/admin/packages', { params: { page, size, status, keyword } })
}

/**
 * 套餐下拉选项（`{ value, label }`），供「新增账号」挑当前套餐用。
 *
 * ⚠️ **不过滤 `status`**：给存量用户指派套餐时，已停售的套餐仍要能选
 *    （演示数据里 1006 / 1013 用的就是那条已停售的学生特惠）。选项文案里
 *    由后端带出「已停售」，让「为什么这里有个停售的」自解释。
 */
export function listPackageOptions() {
  return request.get('/admin/packages/options')
}

/** 新增套餐。字段校验（名称必填、四个数值非负）在后端，与编辑共用一套规则。 */
export function createPackage({ name, monthly_fee, data_quota, voice_quota, sms_quota, description, status }) {
  return request.post('/admin/packages', {
    name,
    monthly_fee,
    data_quota,
    voice_quota,
    sms_quota,
    description,
    status
  })
}

/**
 * 编辑套餐。
 *
 * ⚠️ `id` 与 `create_time` **不随编辑改动**——后端更新时只写业务字段。
 *    `create_time` 是「这个套餐什么时候上架的」，改文案不该改它。
 */
export function updatePackage({ id, name, monthly_fee, data_quota, voice_quota, sms_quota, description, status }) {
  return request.put(`/admin/packages/${id}`, {
    name,
    monthly_fee,
    data_quota,
    voice_quota,
    sms_quota,
    description,
    status
  })
}

/**
 * 删除套餐。
 *
 * ⚠️ **有引用就拒绝，改用「停售」。** 后端会数「当前持有该套餐的账号 +
 *    历史办理记录」，大于 0 就返回带条数的拒绝文案。理由：删除会让历史记录
 *    的 `package_id` 指向不存在的套餐，办理记录显示成「未知套餐」——
 *    一个不报错的静默数据错误。种子数据里套餐 1–5 全部被引用，
 *    所以「删不掉」是常态；正向路径同样是「新建 → 删除」。
 */
export function deletePackage(id) {
  return request.delete(`/admin/packages/${id}`)
}

/* =========================================================================
 * 工单管理
 * ========================================================================= */

/**
 * 全部工单（分页 + 筛选）——**不按 `user_id` 过滤**。
 *
 * ⚠️ 与 `api/ticket.js#listTickets` 恰好相反，且**不能复用它**：那个函数在
 *    后端硬过滤了当前用户。管理员复用它的话，列表里只会出现 1001 的工单，
 *    而种子数据里 1002 的 7901 是全局最新、本该排第一行——少掉它一眼可见。
 *
 * 每行由后端拼好 `user_login_name`（脱敏）与 `handler_name`（未处理为 null）。
 */
export function listAllTickets({ page = 1, size = 5, type, status, keyword } = {}) {
  return request.get('/admin/tickets', { params: { page, size, type, status, keyword } })
}

/**
 * 工单状态流转 —— 本项目**两处真实业务逻辑之二**（另一处是套餐办理）。
 *
 * ⚠️ 《开发文档》§6.6 明确警告：「不可从简到只剩一个下拉框改值」。
 *    合法迁移只有两条：`PENDING → PROCESSING → DONE`，`DONE` 是终态。
 *    服务端按状态机校验，并用 `UPDATE … WHERE status = 当前状态` 的条件更新
 *    挡住「两个标签页同时操作同一张工单」；后到的那个会拿到
 *    「不允许的状态流转，请刷新后重试」。
 *
 *    页面 `views/admin/Tickets.vue` 只渲染当前状态的唯一合法后继，
 *    两层是**互补而非重复**：UI 挡「点错」，后端挡「绕过 UI 的调用」。
 *
 * `handler_id` / `handle_time` 由后端从 token 与当前时钟得出，前端不传。
 */
export function advanceTicket({ id, status }) {
  return request.put(`/admin/tickets/${id}/status`, { status })
}
