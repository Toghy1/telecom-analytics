import { mockOrders } from '@/api/mock/order'
import { mockAccount } from '@/api/mock/account'
import { mockPackages } from '@/api/mock/package'
import { paginate } from '@/api/mock/paginate'
import { nowText } from '@/utils/datetime'

/**
 * 套餐办理与办理记录查询（《开发文档》§6.4 #5）。
 *
 * ⚠️ **每个查询都按 `user_id` 过滤**，取的是当前登录账号。真后端的 `user_id`
 *    来自 JWT，不由前端传；这里用 `mockAccount.id` 顶替，就是「假装我是那个
 *    从 token 里读出来的当前用户」。所以下面几个函数都**不需要调用方传 userId**，
 *    签名与将来的后端接口保持一致。
 *    `mock/order.js` 里混了两条 1002 的记录，正是为了让这个过滤能被测出来。
 */
const CURRENT_USER_ID = mockAccount.id

/** 按办理时间倒序——办理记录要的都是「新的在前」。
 *  `order_time` 是 `YYYY-MM-DD HH:mm:ss` 定宽格式，字典序即时间序。 */
function byOrderTimeDesc(a, b) {
  return b.order_time.localeCompare(a.order_time)
}

/**
 * 把 `package_id` 解析成套餐名与月费。
 *
 * ⚠️ 这里**不能用 `listPackages()`**——它按 `status = 1` 过滤，回答的是
 *    「现在能办哪些」。而办理记录里躺着的是**当年办过的**套餐，其中有已下架的
 *    （`mock/order.js` 第 5 条就是）。用 listPackages 解析，那条会显示成空白，
 *    且不报错、不抛异常，只是页面上少了个套餐名。
 *    `api/package.js` 的 `getPackageById()` 注释里已写明这个区别：两个函数
 *    各自服务一种问题，别拿一个去顶另一个。
 */
function resolvePackage(packageId) {
  const pkg = mockPackages.find((item) => item.id === packageId)
  return {
    package_name: pkg ? pkg.name : '未知套餐',
    monthly_fee: pkg ? pkg.monthly_fee : null
  }
}

/**
 * 办理记录（分页）。
 *
 * 后端就绪后换成：
 *     return request.get('/orders', { params: { page, size } })
 */
export function listOrders({ page = 1, size = 5 } = {}) {
  const rows = mockOrders
    .filter((item) => item.user_id === CURRENT_USER_ID)
    .sort(byOrderTimeDesc)
    .map((item) => ({ ...item, ...resolvePackage(item.package_id) }))

  return Promise.resolve(paginate(rows, page, size))
}

/**
 * 办理 / 变更套餐。
 *
 * 语义按 2026-09-30 决策「套餐办理即时生效」：
 *   - `t_account.package_id` **立即改**，口径是「即时生效」；
 *   - `t_order.status` 固定为 `1`（已生效），不做状态流转；
 *   - **本月不另收月费**——当月账单里已有一笔月费，不再收第二笔。
 *
 * 连带后果（这是刻意的，不是 bug）：降档时新套餐额度可能低于本月已用量，
 * 余量会显示为负数并走红色超套样式。办理页在降档前会弹二次确认把这件事说明白。
 *
 * 办理时间取 `nowText()`（`@/utils/datetime.js`）——与 `t_promo` 的档期判定
 * 用的是同一个格式，业务库时间字段必须处处一致，否则一半能比、一半不能比。
 *
 * 后端就绪后，整个函数体换成：
 *     return request.post('/orders', { package_id })
 */
export function submitOrder({ package_id }) {
  const pkg = mockPackages.find((item) => item.id === package_id)

  if (!pkg) {
    return Promise.reject(new Error('套餐不存在'))
  }
  // 「已下架不能办」与「已停用账号不能办」是两回事，这里只管前者。
  // 用 `status !== 1` 而不是 `!pkg.status`——0 与 undefined 都该拦下。
  if (pkg.status !== 1) {
    return Promise.reject(new Error('该套餐已下架，无法办理'))
  }
  if (package_id === mockAccount.package_id) {
    return Promise.reject(new Error('当前已是该套餐，无需重复办理'))
  }

  const order_time = nowText()

  // ⚠️ 下面三行是**假数据阶段对「真后端写库」的模拟**，接后端时整段删除。
  //
  // 改的是 mock 模块里的对象，刷新页面即还原（等于数据库回到种子数据）。
  // 之所以要真改：只追加一条办理记录、却不改当前套餐的话，页面立刻自相矛盾
  // ——办理记录说刚办了 129 元套餐，顶上「当前套餐」还写着 79 元。
  mockAccount.package_id = package_id
  mockOrders.push({
    id: mockOrders.reduce((max, item) => Math.max(max, item.id), 0) + 1,
    user_id: CURRENT_USER_ID,
    package_id,
    order_time,
    status: 1
  })

  return Promise.resolve({ package_id, package_name: pkg.name, order_time })
}
