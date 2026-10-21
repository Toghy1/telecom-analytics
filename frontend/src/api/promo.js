import request from './request'

/**
 * 查询**进行中**的促销活动。
 *
 * 「进行中」由后端判定，两个条件缺一不可：
 *
 *     ① `status = 1`  —— 运营有没有把这个活动挂出来
 *     ② `start_time <= 现在 <= end_time` —— 活动本身的档期
 *
 * 单看 ① 是不够的：`status` 是手工维护的标记，活动到期后忘了置 0 是运营后台
 * 最常见的疏漏；单看 ② 也不够：还没开始的活动同样不该出现。两者打架时以日期为准。
 * 种子里专门放了三种「只满足其一」的记录（status=0 / 已过期 / 未开始）。
 *
 * ⚠️ 时间比较在 SQL 里做（数据库时间），不在前端比字符串。前端假数据阶段
 *   用定宽字符串比较是当时的替代方案；接线后口径回到服务端一处。
 *
 * ⚠️ 返回里带 `package_name`，由后端批量查出后拼好——**不是**让前端拿到
 *    `package_id` 再逐条查一次（那是 N+1 次请求）。
 *
 * @param {number} [limit] 只取前 N 条。首页「节选」用，不传则返回全部。
 */
export function listPromos({ limit } = {}) {
  return request.get('/promos', { params: { limit } })
}
