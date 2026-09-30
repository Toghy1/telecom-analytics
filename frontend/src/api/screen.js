import {
  mockOfflineMetrics,
  mockPageHeat,
  mockCategoryDist,
  STAT_DATE,
  TOTAL_USERS,
  buildTrend,
  buildHourlyHeatmap,
  buildHeatmapDayLabels
} from './mock/screen'
import { fetchRealtimeSnapshot, subscribeRealtime } from './realtime'
import { shiftDays } from '@/utils/datetime'

/**
 * 大屏数据访问层。
 *
 * 本阶段是「纯前端 + 假数据」：直接读 `src/api/mock/screen.js`。
 * 后端接口就绪后，把每个函数体换成一行 `request.get(...)` 即可，
 * **页面组件一行都不用改**（选型⑤ 纪律①）。
 *
 * 这一层存在的意义不只是「换个地方发请求」，更是**把这几个指标的查询口径
 * 收口到一处**。大屏有四个板块十来个图，若每个板块各自去 mock 里捞一遍，
 * 「地域分布按哪个口径聚合」「漏斗要不要按 session 去重」这类问题就会
 * 在四个地方各答一遍，答得还不一样。
 *
 * 分层原则：**本层负责「取数 + 分组」，`utils/screenCharts.js` 负责「转成
 * ECharts option」，`.vue` 只负责「摆位置」。** 三层各自可以单独看懂。
 */

/* ------------------------------------------------------------------ *
 *  服务端接口编号                                                        *
 * ------------------------------------------------------------------ */

/** 离线指标（板块③④）——一次拉全，前端按 `metric_code` 分组。 */
export function getOfflineMetrics() {
  return Promise.resolve(mockOfflineMetrics)
}

/**
 * 实时指标初值（板块①）。
 * 与 `subscribeRealtime()` 配套，缺一不可——理由见 `api/realtime.js`。
 */
export function getRealtimeSnapshot() {
  return fetchRealtimeSnapshot()
}

/** 原样再导出，让页面只从 `@/api/screen` 取数，不必知道底下是 SSE 还是轮询。 */
export { subscribeRealtime }

/**
 * 留存曲线的观察窗口（天）。横轴从统计日往前数这么多天。
 *
 * 定成常量而不是写死在循环里，是因为这个数字**同时决定了要查多少数据**：
 * 图上要看 14 天，SQL 的 `stat_date >= 统计日 - 13` 就得是同一个数。
 * 两处各写一个字面量 14，改的时候必漏一处——而漏掉的那次不会报错，
 * 只是曲线右半边没有数据，看着像后端没算。
 */
const RETENTION_DAYS = 14

/* ------------------------------------------------------------------ *
 *  板块①：实时核心指标                                                   *
 * ------------------------------------------------------------------ */

/**
 * 把快照行数组转成 `{ online_users: 386, today_pv: 18420, ... }`。
 *
 * 之所以要转：SSE 每次推**一条**记录，页面上是**五个独立的数字卡**，
 * 用 `metric_code` 做键的普通对象最顺手（`counters[code]` 直接取值）。
 * 若保留数组，每次更新都要 `find` 一次，且并发更新时有覆盖风险。
 */
export function toCounterMap(rows) {
  const map = {}
  rows.forEach((row) => {
    map[row.metric_code] = row.metric_value
  })
  return map
}

/* ------------------------------------------------------------------ *
 *  板块②：实时趋势与热度                                                 *
 * ------------------------------------------------------------------ */

/**
 * 访问趋势曲线。`range` 取 `'1h'` 或 `'24h'`。
 *
 * @returns {{stat_time: string, metric_value: number}[]} 升序
 */
export function getTrend(range) {
  return Promise.resolve(buildTrend(range))
}

/**
 * 页面热度 Top N（默认 10）。
 *
 * ⚠️ `limit` 是**入参**，不是页面里 `slice()`——与 `listPackages({ limit })`
 *    同一套写法（决策「游客端采用『首页节选 + 浏览页全量』」）。
 *    接后端时这里换成 `request.get('/screen/page-heat', { params: { limit } })`，
 *    切片由 SQL 的 `ORDER BY ... LIMIT` 做，页面还是不用改。
 *
 * ⚠️ 降序排列放在这一层，**不在图表配置里**。回到接口时顺序由 SQL 决定，
 *    图表只负责画——若图表自己再排一次，将来某天接口改成按页面路径排序，
 *    图表会「顺手」把它排回降序，看起来一切正常，实际掩盖了接口的改动。
 */
export function getPageHeat(limit = 10) {
  const rows = [...mockPageHeat].sort((a, b) => b.metric_value - a.metric_value)
  return Promise.resolve(rows.slice(0, limit))
}

/** 行为类型分布（环形图）。 */
export function getCategoryDist() {
  return Promise.resolve(mockCategoryDist)
}

/* ------------------------------------------------------------------ *
 *  板块③：转化与活跃                                                     *
 * ------------------------------------------------------------------ */

/**
 * 从离线指标行里按 `metric_code` 过滤。
 * 真实实现是一次 SQL 查询，这里模拟「按指标编码取一组行」这一件事。
 */
function pick(metricCode) {
  return mockOfflineMetrics.filter((row) => row.metric_code === metricCode)
}

/**
 * 行为漏斗。按 `FUNNEL_STEP` 的固定顺序返回，**不按数值排序**。
 * 见 `constants.js` 的 `FUNNEL_STEP` 注释。
 */
export function getFunnel() {
  const rows = pick('funnel')
  const map = {}
  rows.forEach((row) => {
    map[row.dimension_value] = row.metric_value
  })
  return Promise.resolve(map)
}

/**
 * 用户留存曲线：近 14 天 cohort 的次日 / 7 日留存率。
 *
 * ⚠️ `d7` 里会出现 `null`，且**必须原样传给图表，不能 `|| 0`**。
 *    缺口是「还算不出来」，不是「留存是 0」。补 0 会把曲线尾巴砸到横轴上，
 *    看起来像留存崩盘——见 `mock/screen.js` 里 `mockRetention` 的说明。
 *    ECharts 折线遇 `null` 自然断开，正是想要的表达。
 *
 * `dimension_value` 的格式是 `'2026-09-17|d1'`（日期|口径）。
 * 之所以把两个维度拼进一个字段：`dws_metric_offline` 的维度列只有一个
 * （`dimension` / `dimension_value`），而留存既是「按日期」又是「按第几日」。
 * 拼在值里是这张表的既定用法，前端拆开即可。
 */
export function getRetention() {
  const rows = pick('retention')

  // 横轴先定下来：截止到统计日的近 14 个自然日。**不来自数据行。**
  // 这是本函数唯一容易写错的地方——按数据推横轴的版本更短，也更像对
  // （「有几天的 cohort 就画几天」），但缺值的那天会被静默抽掉而不是
  // 留空档，图上完全看不出少了。见 `utils/datetime.js#shiftDays`。
  const dates = []
  for (let i = RETENTION_DAYS - 1; i >= 0; i--) {
    dates.push(shiftDays(STAT_DATE, -i))
  }

  const d1Map = {}
  const d7Map = {}

  rows.forEach((row) => {
    const [date, kind] = String(row.dimension_value).split('|')
    if (!date || !kind) return
    if (kind === 'd1') d1Map[date] = row.metric_value
    if (kind === 'd7') d7Map[date] = row.metric_value
  })

  return Promise.resolve({
    dates,
    // 落在轴外（或口径没算出来）的行自然被忽略，轴上这点就是 null。
    // 缺口一律落 null，**绝不用 0 兜底**——0% 留存是「一个人都没回来」，
    // null 是「还不知道」，是两句完全不同的话，画出来一条贴地的线和一段
    // 断线也不一样。
    d1: dates.map((date) => (date in d1Map ? d1Map[date] : null)),
    d7: dates.map((date) => (date in d7Map ? d7Map[date] : null))
  })
}

/** 24 小时时段热力图（近 7 天 × 24 小时）。 */
export function getHourlyHeatmap() {
  return Promise.resolve({
    days: buildHeatmapDayLabels(),
    series: buildHourlyHeatmap()
  })
}

/* ------------------------------------------------------------------ *
 *  板块④：用户维度分布                                                   *
 * ------------------------------------------------------------------ */

/**
 * 地域分布。返回 `[{name, value}]`，`name` 逐字等于地图 geoJSON 的省份名。
 * 见 `mock/screen.js` 的说明——前端不做「市 → 省」的截取。
 */
export function getRegionDist() {
  const rows = pick('region_dist').sort((a, b) => b.metric_value - a.metric_value)
  return Promise.resolve(
    rows.map((row) => ({ name: row.dimension_value, value: row.metric_value }))
  )
}

/** 套餐分布。 */
export function getPackageDist() {
  const rows = pick('package_dist').sort((a, b) => b.metric_value - a.metric_value)
  return Promise.resolve(
    rows.map((row) => ({ name: row.dimension_value, value: row.metric_value }))
  )
}

/** 活跃度分层。顺序固定为 高 → 中 → 低，不按数值排。 */
export function getActivityDist() {
  const order = ['高频活跃', '中频活跃', '低频活跃']
  const rows = pick('activity_level')
  const map = {}
  rows.forEach((row) => {
    map[row.dimension_value] = row.metric_value
  })
  return Promise.resolve(order.map((name) => ({ name, value: map[name] ?? 0 })))
}

export { STAT_DATE, TOTAL_USERS }
