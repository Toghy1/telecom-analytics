/**
 * 账号假数据。
 *
 * ⚠️ 字段名严格对齐《开发文档》§5.2 ① `t_account`，一律 snake_case。
 *    选型⑤ 纪律：前端**不做 camelCase 转换**——字段从头到尾同名，调试时不用脑内映射。
 *
 * ⚠️ `login_name` 与 `id_card` 拿到的**本身就是脱敏态**。
 *    脱敏写在后端 Entity → VO 的转换里（选型⑥），前端不得含任何脱敏代码。
 *    这里的假数据只是「假装自己是后端的输出」。
 *
 * ⚠️ **登录时输入的标识与 `account.login_name` 不是同一个字符串。**
 *    电信用户的登录标识是**手机号**（`13800001234`），而服务端回给前端的
 *    `login_name` 是它的**脱敏态**（`138****1234`）——真实系统就是这个流程：
 *    拿明文比对、回脱敏值。分析员与管理员则以**用户名**登录，两边同形。
 *    不把「入口明文」与「返回脱敏值」分开的话，`login()` 会拿脱敏值去比明文，
 *    永远对不上，而且报错会指向「密码错误」，排查方向完全错。
 */

/**
 * 电信用户。业务页面（order / bill / usage / ticket 四个 api 模块）里的
 * 「当前登录用户」取的就是这个对象——它们拿它顶替「从 JWT 里读出来的 user_id」。
 *
 * ⚠️ `region` 特意取「广东省深圳市」而不是「上海市」——它要能和 tracker.js 里
 *    游客的兜底地域（`DEFAULT_REGION = '上海市'`）区分开。两者若相同，埋点
 *    `region` 那条「登录取 t_account.region / 游客取兜底值」的分支就**测不出区别**：
 *    即便代码写错成永远返回兜底值，所有断言照样全绿。
 *    身份证号前缀 `440301` 与之一致（深圳市福田区），保持数据自洽。
 */
export const mockAccount = {
  id: 1001,
  login_name: '138****1234',
  role: 'USER',
  id_card: '440301********1234',
  region: '广东省深圳市',
  package_id: 2,
  balance: 68.5,
  status: 1,
  create_time: '2024-03-15 10:20:00'
}

/**
 * 运营分析员。见 claude.md 决策「分析平台角色守卫与登录分流（问题 26 归档）」。
 *
 * ⚠️ **没有 `id_card` / `package_id` / `balance`，是刻意的、不是漏写。**
 *    角色表定的「最小必要」原则下，分析员只能看聚合数据、看不到任何个人明细，
 *    真实后端**根本不会把这三个字段返回给这个角色**。这里如实模拟该形状，
 *    顺便让「分析员手里冒出了个人字段」这类回归一眼可见。
 *
 * ⚠️ `region` 取「广东省广州市」，与用户的深圳不同。分析员不产生埋点（角色表），
 *    这个值照理永远不会进日志——正因为如此，一旦哪条断言里冒出它，
 *    就说明 tracker 那道「非 USER 停报」的闸漏了。
 */
export const mockAnalystAccount = {
  id: 2001,
  login_name: 'analyst',
  role: 'ANALYST',
  region: '广东省广州市',
  status: 1,
  create_time: '2024-03-15 10:20:00'
}

/**
 * 系统管理员。账号管理 / 套餐配置 / 工单处理归他；可见用户信息，但手机号、身份证
 * 须脱敏展示（脱敏同样由后端在 Entity → VO 里做，前端不写脱敏代码）。
 *
 * ⚠️ 管理后台**尚未建成**，该角色登录后暂落营业厅首页——见 Login.vue 的 `ROLE_HOME`。
 */
export const mockAdminAccount = {
  id: 3001,
  login_name: 'admin',
  role: 'ADMIN',
  region: '北京市',
  status: 1,
  create_time: '2024-03-15 10:20:00'
}

/**
 * 可登录账号表。
 *
 * ⚠️ **口令这里写的是明文。** 真实系统里服务端比对的是 BCrypt 哈希，
 *    前端连哈希都拿不到、更拿不到明文。写明文只为演示阶段能一眼看出密码是什么，
 *    别当成设计。
 *
 * 三个账号共用同一个口令，是为了演示时不必记三串密码——**切角色只换登录标识**。
 * 登录页的演示提示据此只写一次口令。
 */
export const DEMO_ACCOUNTS = [
  { login_name: '13800001234', password: '123456', account: mockAccount },
  { login_name: 'analyst', password: '123456', account: mockAnalystAccount },
  { login_name: 'admin', password: '123456', account: mockAdminAccount }
]
