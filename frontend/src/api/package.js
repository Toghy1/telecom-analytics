import request from './request'

/**
 * 查询在售套餐列表。
 *
 * 选型⑤纪律①：所有请求集中封装于 src/api/，页面组件不直接调 axios。
 *
 * @param {number} [limit] 只取前 N 条。首页「节选」用，不传则返回全部。
 *   刻意做成入参而不是让首页自己 `slice()`：「首页只展示几个」这个数字
 *   留在调用方，不散进页面模板。
 */
export function listPackages({ limit } = {}) {
  // 升序由后端排（`monthly_fee ASC`），前端不再排一遍——排序规则只有一份。
  return request.get('/packages', { params: { limit } })
}

/**
 * 按 id 查单个套餐。账户总览用它把 `package_id` 换成套餐详情。
 *
 * ⚠️ 与 `listPackages` 不同，后端这个接口**不过滤 status**：
 *   它回答的是「这个 id 对应哪个套餐」，不是「这个套餐现在能不能办」。
 *   账户当前订购的套餐若已下架，用户仍应看到自己订的是什么。
 *
 * 查不到时 `data` 为 `null`（不是 404），页面按 null 处理。
 */
export function getPackageById(id) {
  return request.get(`/packages/${id}`)
}
