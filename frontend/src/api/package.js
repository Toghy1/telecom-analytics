import { mockPackages } from './mock/package'

/**
 * 查询在售套餐列表。
 *
 * 本阶段是「纯前端 + 假数据」：直接返回 mock。
 * 后端接口就绪后，把函数体换成下面这一行即可，**页面组件一行都不用改**：
 *
 *     return request.get('/packages', { params: { limit } })
 *
 * —— 这正是选型⑤纪律①「所有请求集中封装于 src/api/」的用处。
 *
 * @param {number} [limit] 只取前 N 条。首页「节选」用，不传则返回全部。
 *   刻意做成入参而不是让首页自己 `slice()`：接后端时首页无需改动，
 *   且「首页只展示几个」这个数字留在调用方，不散进页面模板。
 */
export function listPackages({ limit } = {}) {
  const rows = mockPackages
    .filter((item) => item.status === 1)
    // 按月费升序。营业厅套餐列表这么排是常识，但这里还有第二个理由：
    // 首页只展示前 3 个，而**停用的「学生特惠 29 元套餐」在升序里正好排第 2**
    // （19 / 29停用 / 39 / 79 / 129）。一旦 `status === 1` 这行过滤失效，
    // 它会立刻挤进首页前三个里被看见——若它排在末尾，首页节选永远轮不到它，
    // 这条过滤写没写、写对没写对，看首页都是同一个样子。
    .sort((a, b) => a.monthly_fee - b.monthly_fee)

  return Promise.resolve(limit ? rows.slice(0, limit) : rows)
}

/**
 * 按 id 查单个套餐。账户总览要用它把 `t_account.package_id` 换成套餐详情，
 * 促销活动页也要用它把 `t_promo.package_id` 换成套餐名。
 *
 * ⚠️ 与 `listPackages` 不同，本函数**不过滤 `status`**：
 *   它回答的是「这个 id 对应哪个套餐」，不是「这个套餐现在能不能办」。
 *   账户当前订购的套餐若已下架，用户仍应看到自己订的是什么。
 *
 * 后端就绪后换成：return request.get(`/packages/${id}`)
 */
export function getPackageById(id) {
  const found = mockPackages.find((item) => item.id === id) || null
  return Promise.resolve(found)
}
