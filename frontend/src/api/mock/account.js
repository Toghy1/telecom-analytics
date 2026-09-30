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
 * ⚠️ 管理后台**已于 2026-09-30 建成**（`/admin/users`），该角色登录后即落那里
 *    ——见 Login.vue 的 `ROLE_HOME` 与 router/index.js 的 `/admin` 路由。
 *
 * ⚠️ 这个 id（3001）在 `api/admin.js` 里被两处复用：① 工单流转时顶替「JWT 里的管理员」
 *    填进 `handler_id`；② 账号删改前的自锁检查（不能停用/删除当前登录的账号）。
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

/* ===========================================================================
 * 以下为管理后台（`/admin/users`）所需。
 * =========================================================================== */

/**
 * 【这是假后端在扮演真后端，不是前端脱敏】
 *
 * 项目红线是「**前端一行脱敏代码都不能有**」——脱敏写在后端 Entity → VO 的转换里
 * （选型⑥），组件与页面只管把拿到手的值原样绑上去。那两个函数下面这两个
 * 干的就是**后端那一环**：`api/mock/` 目录从建立起，定位就是「假装自己是后端的输出」。
 *
 * ⚠️ 因此有一条硬纪律：**`src/views/` 与 `src/components/` 下永远不许 import 它们。**
 *    接后端时整个 `api/mock/` 目录连文件一起删除，`createAccount()` 改成
 *    `request.post('/admin/accounts', payload)`——明文随请求体交给真后端脱敏，
 *    与真实流程完全一致。届时这两个函数**应当无处可删**（若还有引用，说明红线破了）。
 *
 * 为什么需要它们：管理后台「新增账号」要收一个**明文手机号**（用户填的是自己的号码），
 * 而列表里显示的必须是脱敏态。这个转换总得发生在某一层，放在 mock 层是唯一
 * 既不破红线、又不让页面显示明文的落点。
 *
 * 非 11 位手机号 / 18 位身份证**原样返回**：分析员与管理员用用户名登录
 * （`analyst` / `admin`），本就不该被套上手机号的脱敏规则——套了会变成
 * `ana***lyst` 那种东西，比不脱敏更糟。
 */
export function toMaskedLoginName(raw) {
  const s = String(raw ?? '').trim()
  return /^\d{11}$/.test(s) ? `${s.slice(0, 3)}****${s.slice(-4)}` : s
}

export function toMaskedIdCard(raw) {
  const s = String(raw ?? '').trim()
  return /^\d{18}$/.test(s) ? `${s.slice(0, 6)}********${s.slice(-4)}` : s
}

/**
 * 账号列表（管理后台的「用户管理」页）。
 *
 * ⚠️ **可变的**：`api/admin.js` 的增删改会真的动它，模拟「真后端写库」。
 *    刷新页面即还原，等于数据库回到种子数据。
 *
 * ⚠️ 前三条**直接引用**上面那三个对象，而不是各抄一份。抄一份的后果是
 *    「同一个账号在两个页面显示不同的套餐」——而那种错误不报错，
 *    只是数字对不上，要人工比对才发现。
 *
 * ⚠️ **1001 与 1002 必须在列表里。** 它们是全项目其余假数据引用的两个 user_id：
 *    `mock/bill.js`、`mock/order.js`、`mock/usage.js`、`mock/ticket.js` 都有
 *    `user_id: 1001` 或 `1002` 的记录。列表里缺一个，管理员工单页的「提交人」列
 *    就会显示成「未知用户」——而那看起来像 bug，实际是这里漏了一条。
 *
 * 假数据的证伪条件（每一条都对应一类可能的实现错误）：
 *   · `status: 0` 有 2 条（1004 / 1008）——证伪「状态筛选写成了 `if (status)`」，
 *     那种写法下「停用」这一档会**静默查不到任何东西**，页面只是空列表、不报错
 *   · `package_id` 覆盖 1–5，含**已停售的 5**——证伪「解析套餐名时误用
 *     `listPackages()`」（那个函数按 `status === 1` 过滤，已停售的套餐解析不出来）
 *   · `balance` 有 0 与非 0——证伪「`balance || '--'`」，那会把余额 0 显示成 `--`
 *   · 归属地跨 8 个省市、登录标识前缀各异——让关键词筛选有可证伪性
 *   · 2001 / 3001 **刻意没有** `id_card` / `package_id` / `balance`
 *     （与 `mockAnalystAccount` 同理，见该对象上方注释）——证伪「表格里直接
 *     取 `row.balance.toFixed()`」，那一行会崩；正确处理是显示 `--`
 */
export const mockAccounts = [
  mockAccount, // 1001 演示账号
  {
    id: 1002,
    login_name: '139****5678',
    role: 'USER',
    id_card: '440104********5678',
    region: '广东省广州市',
    // 与 mock/order.js 里 1002 那条 129 元办理记录、mock/bill.js 里 129.00 月费咬合
    package_id: 3,
    balance: 129.0,
    status: 1,
    create_time: '2023-11-02 09:30:00'
  },
  mockAnalystAccount, // 2001
  mockAdminAccount, // 3001（也随 mock/account.js 一同构成「当前登录管理员」）
  {
    id: 1003,
    login_name: '137****9012',
    role: 'USER',
    id_card: '440104********9012',
    region: '广东省广州市',
    package_id: 1,
    balance: 12.0,
    status: 1,
    create_time: '2024-05-08 14:12:00'
  },
  {
    id: 1004,
    login_name: '186****3456',
    role: 'USER',
    id_card: '110108********3456',
    region: '北京市',
    package_id: 4,
    balance: 0, // ⚠️ 余额为 0，不是没有余额
    status: 0, // ⚠️ 停用
    create_time: '2024-07-19 08:45:00'
  },
  {
    id: 1005,
    login_name: '135****7788',
    role: 'USER',
    id_card: '310115********7788',
    region: '上海市',
    package_id: 2,
    balance: 45.6,
    status: 1,
    create_time: '2024-09-01 11:30:00'
  },
  {
    id: 1006,
    login_name: '136****2233',
    role: 'USER',
    id_card: '320105********2233',
    region: '江苏省南京市',
    package_id: 5, // ⚠️ 已停售的学生特惠，仍在用的历史存量
    balance: 5.0,
    status: 1,
    create_time: '2024-11-23 16:05:00'
  },
  {
    id: 1007,
    login_name: '158****6677',
    role: 'USER',
    id_card: '330106********6677',
    region: '浙江省杭州市',
    package_id: 3,
    balance: 200.0,
    status: 1,
    create_time: '2025-01-14 09:20:00'
  },
  {
    id: 1008,
    login_name: '159****1122',
    role: 'USER',
    id_card: '510107********1122',
    region: '四川省成都市',
    package_id: 1,
    balance: 0,
    status: 0, // ⚠️ 停用
    create_time: '2025-03-06 13:55:00'
  },
  {
    id: 1009,
    login_name: '177****8899',
    role: 'USER',
    id_card: '440301********8899',
    region: '广东省深圳市',
    package_id: 2,
    balance: 68.5,
    status: 1,
    create_time: '2025-04-28 10:10:00'
  },
  {
    id: 1010,
    login_name: '188****4455',
    role: 'USER',
    id_card: '420106********4455',
    region: '湖北省武汉市',
    package_id: 3,
    balance: 130.5,
    status: 1,
    create_time: '2025-06-17 15:40:00'
  },
  {
    id: 1011,
    login_name: '131****3344',
    role: 'USER',
    id_card: '610113********3344',
    region: '陕西省西安市',
    package_id: 4,
    balance: 3.2,
    status: 1,
    create_time: '2025-08-09 07:35:00'
  },
  {
    id: 1012,
    login_name: '133****5566',
    role: 'USER',
    id_card: '440104********5566',
    region: '广东省广州市',
    package_id: 2,
    balance: 79.0,
    status: 1,
    create_time: '2025-10-21 18:25:00'
  },
  {
    id: 1013,
    login_name: '150****9900',
    role: 'USER',
    id_card: '110108********9900',
    region: '北京市',
    package_id: 5, // ⚠️ 已停售
    balance: 29.0,
    status: 1,
    create_time: '2026-01-11 12:00:00'
  },
  {
    id: 1014,
    login_name: '152****7711',
    role: 'USER',
    id_card: '310115********7711',
    region: '上海市',
    package_id: 1,
    balance: 39.0,
    status: 1,
    create_time: '2026-02-26 09:05:00'
  }
]
