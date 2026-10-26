import { mockPromos } from '@/api/mock/promo'
import { mockPackages } from '@/api/mock/package'
import { nowText } from '@/utils/datetime'

/**
 * 查询**进行中**的促销活动。
 *
 * 与 listPackages 同理，接后端时把函数体换成
 *     return request.get('/promos', { params: { limit } })
 * 页面无需改动。
 *
 * ⚠️ 「进行中」由两个条件共同决定，缺一不可：
 *
 *     ① `status === 1`  —— 运营有没有把这个活动挂出来
 *     ② `start_time <= 现在 <= end_time` —— 活动本身的档期
 *
 *    单看 ① 是不够的：`status` 是一个**手工维护的标记**，活动到期后忘了置 0
 *    是运营后台最常见的疏漏，而 `end_time` 是活动自己带的档期，它不会忘。
 *    两者打架时以日期为准——「这个活动明明写着 8 月底就结束了，怎么还在首页挂着」
 *    是用户一眼就能看出来的问题，比运营后台少改一个字段严重得多。
 *    假数据里 `id=4` 那条就是这个情形（status=1 但档期已过），专门用来证伪这条。
 *
 * ⚠️ 下面用的是**定宽字符串比较**（`YYYY-MM-DD HH:mm:ss`），不是
 *    `new Date(...) > new Date(...)`。为什么不那么写，见
 *    `@/utils/datetime.js` —— 时区差 8 小时，活动最后一天晚上会显示成「已结束」。
 *    取当前时刻统一走 `nowText()`，全站只有那一个实现。
 *
 * @param {number} [limit] 只取前 N 条。首页「节选」用，不传则返回全部。
 */
export function listPromos({ limit } = {}) {
  const ts = nowText()

  const rows = mockPromos
    .filter((item) => item.status === 1 && item.start_time <= ts && ts <= item.end_time)
    // 新活动排前面。活动列表按档期倒序是常识，也让「首页节选」取到的是最近的活动。
    .sort((a, b) => b.start_time.localeCompare(a.start_time))
    // 把 package_id 解析成套餐名。
    // ⚠️ 真实后端这一步是 SQL JOIN（`t_promo LEFT JOIN t_package`），
    //    不会让前端拿到 id 再去查一次——那样 N 条活动就是 N+1 次请求。
    //    这里在 api 层一次做完，页面拿到的就是可以直接渲染的字段。
    .map((item) => {
      const pkg = mockPackages.find((p) => p.id === item.package_id)
      return { ...item, package_name: pkg ? pkg.name : '' }
    })

  return Promise.resolve(limit ? rows.slice(0, limit) : rows)
}
