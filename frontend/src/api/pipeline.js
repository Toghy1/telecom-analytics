import request from './request'
import { DIRTY_RULE, DIRTY_SOURCE } from '@/utils/constants'

/**
 * 子页 · 系统运行分析的数据访问层。
 *
 * 四块内容里，前三块来自一次聚合查询（`/screen/pipeline/summary`），
 * 第四块是独立的分页查询（`/screen/dead-letters`）。页面组件一行未改。
 *
 * ⚠️ 子页与主页**同为分析平台，但取数方式不同**：主页的实时部分走 SSE，
 *    子页走普通 REST + 定时刷新（决策「分析平台采用『主页 + 子页』结构」
 *    明确「子页不接 SSE」）。这条差异在 api 层就已经体现——
 *    本文件里没有任何 `subscribe` 函数，而 `api/screen.js` 有。
 */

/* ------------------------------------------------------------------ *
 *  ① 今日管道量                                                          *
 * ------------------------------------------------------------------ */

/**
 * 今日管道量。
 *
 * ⚠️ **入库率在这里算，不在页面里算。**
 *    与决策「余量计算落在后端，前端不做减法」同一个道理：比率牵着一个
 *    不报错的坑——`collected` 若被换成「入库+判脏」的另一种口径，
 *    比率会算错但页面照常渲染。收口到一处就是收口成一个入口。
 *    接后端时这个数由接口直接给出，本函数体换成一行 `request.get` 即可。
 *
 * @returns {{date, collected, dirty, stored, passRate}}
 *   `passRate` 为入库率（百分比，保留 1 位小数）。
 */
export function getPipelineToday() {
  return request
    .get('/screen/pipeline/summary')
    .then((summary) => summary.today)
}

/* ------------------------------------------------------------------ *
 *  ② 脏数据占比趋势                                                      *
 * ------------------------------------------------------------------ */

/**
 * @param {7|30} days 近 7 天或近 30 天
 */
export function getDirtyRatioTrend(days = 7) {
  return request
    .get('/screen/pipeline/summary')
    .then((summary) => (days === 30 ? summary.trend30d : summary.trend7d))
}

/* ------------------------------------------------------------------ *
 *  ③ 规则命中分布                                                        *
 * ------------------------------------------------------------------ */

/**
 * 七条判脏规则的命中数。
 *
 * ⚠️⚠️ **横轴取自 `DIRTY_RULE` 常量，不取自数据。**
 *    这是本文件里最要紧的一行设计。数据里若少了某条规则的记录，
 *    「按数据画图」的结果是那张图上**少一根柱子**——七条变六条，
 *    渲染完全正常，谁也不会去数。而「这条规则到底跑没跑」恰恰是
 *    验收时要看的东西（脏数据过滤是必考环节）。
 *
 *    所以这里以常量键为准逐条去取，取不到就给 0：
 *    **没命中的规则画成 0，一眼可见；漏实现的规则同样画成 0，同样一眼可见。**
 *    按数据画的话，这两种情况在图上长得都像「没有这条规则」。
 *
 *    顺带：命中数合计 = 834，必须等于 ① 里的「判脏」。两处对不上
 *    就说明有一处漏算了规则——这是子页唯一能自查的算术约束。
 */
export function getRuleHits() {
  return request.get('/screen/pipeline/summary').then((summary) => {
    // 轴仍然取自常量，后端只给「每个编码命中多少」。
    // 这样某条规则今天零命中时它画成 0 值柱，而不是从图上消失。
    const rows = Object.entries(DIRTY_RULE).map(([code, meta]) => ({
      code,
      label: meta.label,
      value: summary.ruleHits[code] ?? 0
    }))
    const total = rows.reduce((sum, row) => sum + row.value, 0)
    return { rows, total }
  })
}

/* ------------------------------------------------------------------ *
 *  ④ 死信明细                                                           *
 * ------------------------------------------------------------------ */

/**
 * 死信明细分页查询。
 *
 * 返回**与 MyBatis-Plus `Page<T>` 同形**（`total` / `pages` / `current` /
 * `records`），走 `mock/paginate.js`——与营业厅各列表页同一套分页契约
 * （决策「前端 REST 分页口径定稿」）。子页不另立一套。
 *
 * ⚠️ `source` 过滤**必须真的过滤**。假数据里两种来源都有
 *    （REALTIME 8 条 / OFFLINE 4 条），所以这条过滤写没写、写对没写对，
 *    切一次下拉框就能看出来——若数据里只有一种来源，筛与不筛长得一样。
 *
 * 后端就绪后换成：
 *     return request.get('/screen/dead-letters', { params: { page, size, source } })
 *
 * @param {{page?: number, size?: number, source?: string}} params
 *   `source` 传空（`''` / `undefined`）表示不过滤。
 */
export function listDeadLetters({ page = 1, size = 5, source = '' } = {}) {
  // 排序（fail_time 倒序）与分页都在 SQL 里做，前端不再排序——
  // 否则接口换了排序口径，前端会「顺手」把它排回原样，掩盖改动。
  return request.get('/screen/dead-letters', {
    params: { page, size, source }
  })
}

/** 死信来源下拉项。 */
export function getSourceOptions() {
  return Object.entries(DIRTY_SOURCE).map(([code, meta]) => ({
    value: code,
    label: meta.label
  }))
}
