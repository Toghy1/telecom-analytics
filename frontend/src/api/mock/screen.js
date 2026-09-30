/**
 * 分析平台 · 大屏假数据（主页四板块 + 子页系统运行分析的数据基础）。
 *
 * ⚠️ 两条硬约束，写假数据时就必须遵守，否则接后端时页面要返工：
 *
 *   ① **形状对齐真实表。** 离线指标一律长成 `dws_metric_offline` 的样子
 *      （`metric_code` / `dimension` / `dimension_value` / `stat_date` /
 *      `metric_value`），实时指标一律长成 `dws_metric_realtime` 的样子
 *      （`metric_code` / `metric_value` / `stat_time`）。前端不在这一层
 *      另造一套「图表友好」的结构——那样接后端时要在 api 层写一大堆
 *      形状转换，而转换代码是最容易悄悄写错的地方（少映射一个字段不报错，
 *      只是图上少一条）。
 *
 *   ② **假数据必须能证伪**（claude.md 决策「假数据必须能证伪过滤条件」）。
 *      大屏没有「按用户过滤」这种东西，但**同一批总量在多个口径下必须对得上**，
 *      这条就是大屏版的证伪手段：地域分布、套餐分布、活跃度分层三个板块
 *      统计的都是同一批用户，三者合计**必须都等于 `TOTAL_USERS`**。
 *      任何一个口径的聚合写漏了一类（比如套餐分布只 join 了在售套餐），
 *      它的合计就会与另外两个对不上——不需要知道正确答案，三个数互相印证。
 *
 * 关于量级：这里的「用户」是批量造数脚本造出的**全量用户**（`TOTAL_USERS` 人），
 * 不是业务库里那 5 个演示账号。大屏是聚合视图，拿 5 个人画地域图会是
 * 「五个省各 1 人」——数字对，但没有可用性。造数脚本读真实业务库的
 * 套餐与归属地（决策「离线数据由批量造数脚本生成文件经 SFTP 上传」），
 * 造出的分布才是这一份。
 */

// ⚠️ 这两个函数只管**格式**与**分桶对齐**，与 `utils/tracker.js` 里那种
//    「前端自己生成时间」不同——曲线上的 `stat_time` 真实系统里由后端落库时写，
//    这里只是让假数据长得一样。用公共实现而不是就地写一份，
//    理由与 `utils/datetime.js` 的注释一致：格式必须全站唯一。
import { formatDateTime, floorToBucket } from '@/utils/datetime'

/** 离线指标的统计日。真实系统里由 Spark 作业的调度日期决定。 */
export const STAT_DATE = '2026-09-30'

/**
 * 用户总数。**地域 / 套餐 / 活跃度三个口径合计都必须等于它。**
 * 这是一个交叉校验锚点——见文件头说明。
 */
export const TOTAL_USERS = 1280

/** `dws_metric_offline` 的一行。 */
function offlineRow(metric_code, dimension, dimension_value, metric_value) {
  return { metric_code, dimension, dimension_value, stat_date: STAT_DATE, metric_value }
}

/**
 * 地域分布（板块④，离线链路）。
 *
 * ⚠️ `dimension_value` 必须是**省级**名称，且**逐字等于 `public/map/china.json`
 *    里 `properties.name` 的取值**（"广东省" 而非 "广东"，"内蒙古自治区" 而非
 *    "内蒙古"）。ECharts 的 map 系列是按名字精确匹配的——名字对不上，
 *    该省**不报错、不变色**，就是在地图上没有数据。这是接地图时最常见的坑。
 *
 * ⚠️ **聚合到省级这一步发生在离线链路（Spark），不在前端。**
 *    `t_account.region` 存的是 "广东省深圳市" 这种带城市的串，若让前端
 *    截字符串去省份，规则根本立不住：'内蒙古自治区' / '广西壮族自治区' /
 *    '黑龙江省' 长度各不相同，'北京市' 干脆没有 '省' 字。一条 `slice(0,3)`
 *    在广东对、在黑龙江错、在北京错得更离谱，而且**错了不报错**，
 *    只是地图上某个省的数字凭空少了一块。所以离线作业直接按省份聚合好，
 *    `dimension_value` 落库时就已经是省名。
 *
 * 合计 = 1280，见文件头。
 */
export const mockRegionDist = [
  offlineRow('region_dist', 'region', '广东省', 216),
  offlineRow('region_dist', 'region', '江苏省', 104),
  offlineRow('region_dist', 'region', '浙江省', 96),
  offlineRow('region_dist', 'region', '山东省', 88),
  offlineRow('region_dist', 'region', '河南省', 76),
  offlineRow('region_dist', 'region', '四川省', 62),
  offlineRow('region_dist', 'region', '河北省', 54),
  offlineRow('region_dist', 'region', '湖北省', 50),
  offlineRow('region_dist', 'region', '湖南省', 48),
  offlineRow('region_dist', 'region', '福建省', 44),
  offlineRow('region_dist', 'region', '北京市', 42),
  offlineRow('region_dist', 'region', '上海市', 40),
  offlineRow('region_dist', 'region', '安徽省', 38),
  offlineRow('region_dist', 'region', '辽宁省', 34),
  offlineRow('region_dist', 'region', '陕西省', 32),
  offlineRow('region_dist', 'region', '江西省', 30),
  offlineRow('region_dist', 'region', '重庆市', 28),
  offlineRow('region_dist', 'region', '广西壮族自治区', 26),
  offlineRow('region_dist', 'region', '山西省', 24),
  offlineRow('region_dist', 'region', '云南省', 22),
  offlineRow('region_dist', 'region', '黑龙江省', 20),
  offlineRow('region_dist', 'region', '贵州省', 18),
  offlineRow('region_dist', 'region', '吉林省', 16),
  offlineRow('region_dist', 'region', '天津市', 16),
  offlineRow('region_dist', 'region', '内蒙古自治区', 14),
  offlineRow('region_dist', 'region', '新疆维吾尔自治区', 12),
  offlineRow('region_dist', 'region', '甘肃省', 10),
  offlineRow('region_dist', 'region', '海南省', 8),
  offlineRow('region_dist', 'region', '宁夏回族自治区', 6),
  offlineRow('region_dist', 'region', '青海省', 4),
  offlineRow('region_dist', 'region', '西藏自治区', 2)
]

/**
 * 套餐分布（板块④，离线链路）。按用户**当前**套餐统计
 * （决策「套餐模型为一用户一套餐」：一个用户必然且仅归属一个套餐）。
 *
 * ⚠️ **必须包含已下架的「学生特惠 29 元套餐」（id 5，`status = 0`）。**
 *    这正是最容易被写错的一处：离线作业若 join 了 `t_package WHERE status = 1`，
 *    下架前办理了该套餐的用户会**整批消失**。他们并没有换套餐，只是套餐下架了。
 *    表现是套餐分布合计变成 1154，与地域分布的 1280 对不上——
 *    而单看套餐分布那张饼图，六个扇区变五个，看着完全正常。
 *    这条数据就是为了让那个错**当场显形**。
 *
 * 合计 = 1280，见文件头。
 */
export const mockPackageDist = [
  offlineRow('package_dist', 'package', '畅享 5G 79 元套餐', 418),
  offlineRow('package_dist', 'package', '畅享 5G 39 元套餐', 302),
  offlineRow('package_dist', 'package', '孝心卡 19 元套餐', 248),
  offlineRow('package_dist', 'package', '畅享 5G 129 元套餐', 186),
  offlineRow('package_dist', 'package', '学生特惠 29 元套餐', 126)
]

/**
 * 用户活跃度分层（板块④，离线链路）。
 * 口径：近 30 天行为事件数 —— 高频 ≥ 50、中频 10–49、低频 < 10。
 *
 * 合计 = 1280，见文件头。
 */
export const mockActivityDist = [
  offlineRow('activity_level', 'level', '高频活跃', 268),
  offlineRow('activity_level', 'level', '中频活跃', 612),
  offlineRow('activity_level', 'level', '低频活跃', 400)
]

/**
 * 行为漏斗（板块③，离线链路）：访问 → 登录 → 查询 → 办理。
 *
 * ⚠️ 口径是**会话级**（`session_id` 去重），不是事件级。
 *    事件级算出来不单调：一个人可以登录 1 次、查询 8 次，漏斗中段会鼓起来，
 *    画成漏斗是畸形。会话级天然单调递减，也是漏斗图唯一说得通的口径。
 *    `session_id` 由前端生成、游客也有（决策「统一日志规范」），所以第一段
 *    「访问」能把游客算进来——这正是需要保留游客角色的原因。
 */
export const mockFunnel = [
  offlineRow('funnel', 'step', 'view_home', 8640),
  offlineRow('funnel', 'step', 'login', 3180),
  offlineRow('funnel', 'step', 'query', 2460),
  offlineRow('funnel', 'step', 'order_package', 415)
]

/**
 * 用户留存曲线（板块③，离线链路）：近 14 天注册 cohort 的次日 / 7 日留存率。
 *
 * ⚠️⚠️ **`d7` 为 `null` 的 cohort 是不可计算的，绝不能补 0。**
 *    7 日留存要求 cohort 注册满 7 天。今天（09-30）注册的用户，7 日留存
 *    要等到 10-07 才知道——现在**还没有这个数**，不是「留存率是 0%」。
 *    补 0 会把曲线尾巴直接砸到横轴上，看起来像「最近一周留存崩了」，
 *    而真相是「最近一周的数据还没到能算的时候」。ECharts 的折线遇到
 *    `null` 会自然断开，这正是想要的表达：**断掉表示没有数据，
 *    落到 0 表示数据是 0**，两者在图上必须是两种样子。
 *
 *    同理 09-30 这一天的 `d1` 也是 null——次日留存要等到 10-01。
 *    这一条是拿真实后端时最容易「顺手补 0」的地方，故写在此处立据。
 */
export const mockRetention = [
  offlineRow('retention', 'day', '2026-09-17|d1', 41.2),
  offlineRow('retention', 'day', '2026-09-17|d7', 22.8),
  offlineRow('retention', 'day', '2026-09-18|d1', 39.6),
  offlineRow('retention', 'day', '2026-09-18|d7', 21.4),
  offlineRow('retention', 'day', '2026-09-19|d1', 43.8),
  offlineRow('retention', 'day', '2026-09-19|d7', 24.1),
  offlineRow('retention', 'day', '2026-09-20|d1', 46.5),
  offlineRow('retention', 'day', '2026-09-20|d7', 26.3),
  offlineRow('retention', 'day', '2026-09-21|d1', 44.9),
  offlineRow('retention', 'day', '2026-09-21|d7', 23.7),
  offlineRow('retention', 'day', '2026-09-22|d1', 40.3),
  offlineRow('retention', 'day', '2026-09-22|d7', 20.9),
  offlineRow('retention', 'day', '2026-09-23|d1', 38.7),
  offlineRow('retention', 'day', '2026-09-23|d7', 19.6),
  offlineRow('retention', 'day', '2026-09-24|d1', 42.1),
  offlineRow('retention', 'day', '2026-09-25|d1', 45.4),
  offlineRow('retention', 'day', '2026-09-26|d1', 47.2),
  offlineRow('retention', 'day', '2026-09-27|d1', 44.6),
  offlineRow('retention', 'day', '2026-09-28|d1', 41.8),
  offlineRow('retention', 'day', '2026-09-29|d1', 39.2)
]

/** 离线指标全表。真实系统里这是一次 `SELECT * FROM dws_metric_offline WHERE stat_date = ?`。 */
export const mockOfflineMetrics = [
  ...mockRegionDist,
  ...mockPackageDist,
  ...mockActivityDist,
  ...mockFunnel,
  ...mockRetention
]

/* ------------------------------------------------------------------ *
 *  实时链路（板块①②）                                                  *
 * ------------------------------------------------------------------ */

/**
 * 实时指标的初始值（`dws_metric_realtime` 形状）。
 *
 * 这些是「打开大屏那一刻」的值，随 SSE 逐个刷新。之所以要有个初值：
 * SSE 只推**变化**，首帧若等推送才显示，数字卡会先空一格再跳出来。
 * 真实后端由 REST 端点给初值，前端订上 SSE 后增量覆盖——
 * 这正是 `getRealtimeSnapshot()` + `subscribeRealtime()` 两个入口的原因。
 *
 * `metric_code` 与后端实时消费程序里写死的编码必须一致，改一处要改两处。
 */
export const mockRealtimeSnapshot = [
  { metric_code: 'online_users', metric_value: 386, stat_time: null },
  { metric_code: 'today_pv', metric_value: 18420, stat_time: null },
  { metric_code: 'today_uv', metric_value: 2740, stat_time: null },
  { metric_code: 'today_orders', metric_value: 415, stat_time: null },
  { metric_code: 'today_tickets', metric_value: 96, stat_time: null }
]

/**
 * 页面热度（板块②，实时链路）—— 按 `dwd_behavior_log.page` 统计当日 PV。
 *
 * ⚠️ 这里放**全部 11 个页面**，图上只画 Top 10（`api/screen.js` 里切片）。
 *    11 条与 10 条之差就是这条 `slice` 的证伪物：切片若失效，图上会冒出
 *    第 11 根柱子（`/user/ticket`，最少的那条）。
 *    若这里只放 10 条，切不切片画出来一模一样——过滤条件写没写都看不出来。
 *
 * 与业务库的对应关系：这 11 条路径就是路由表里全部带 `meta.track` 的页面
 * 加上 `/login`，一个不多一个不少。**新增页面而忘了同步这张表，
 * 该页面的热度会从图上消失且不报错**，故在此立据。
 */
export const mockPageHeat = [
  { page: '/user/dashboard', metric_value: 3180 },
  { page: '/', metric_value: 2960 },
  { page: '/login', metric_value: 2410 },
  { page: '/user/balance', metric_value: 2240 },
  { page: '/user/usage', metric_value: 1980 },
  { page: '/packages', metric_value: 1720 },
  { page: '/user/bills', metric_value: 1560 },
  { page: '/promos', metric_value: 1240 },
  { page: '/user/order', metric_value: 890 },
  { page: '/user/profile', metric_value: 640 },
  { page: '/user/ticket', metric_value: 520 }
]

/**
 * 行为类型分布（板块②，实时链路）—— 按行为大类聚合的事件数。
 *
 * ⚠️ 键取自 `constants.js` 的 `EVENT_CATEGORY_LABEL`，**六个大类一个不少**。
 *    与判脏规则同理：漏掉一类不报错，只是环形图少一个扇区。
 *
 * 合计 20590，与 `today_pv` 不同口径（PV 按页面计，这里是按事件计），
 * 两者不必相等——**故意不相等**，免得有人误以为它们该对得上而对不上的时候
 * 去改错的那一边。
 */
export const mockCategoryDist = [
  { category: 'BROWSE', metric_value: 5920 },
  { category: 'QUERY', metric_value: 5780 },
  { category: 'ACCOUNT', metric_value: 3820 },
  { category: 'AUTH', metric_value: 3660 },
  { category: 'ORDER', metric_value: 890 },
  { category: 'SERVICE', metric_value: 520 }
]

/* ------------------------------------------------------------------ *
 *  确定性噪声                                                          *
 * ------------------------------------------------------------------ */

/**
 * 确定性伪随机：同一个 `seed` 永远得到同一个数。
 *
 * ⚠️ 这里**刻意不用 `Math.random()`**。趋势曲线、热力图这些由「时间桶 + 噪声」
 *    生成的序列，如果每次刷新都变，会出现两个问题：
 *      1. 自动化验证没法断言具体数值，只能断言「是个数」——等于没验证；
 *      2. 答辩演示时同一页刷新两次数不一样，看起来像数据在抖，实际是假数据在抖。
 *    用一个纯函数的噪声，让这份假数据**可复现**，是「假数据也要当真的写」的一部分。
 */
function noise(seed) {
  const x = Math.sin(seed * 12.9898) * 43758.5453
  return x - Math.floor(x)
}

/**
 * 访问趋势曲线（板块②，实时链路）。
 *
 * - `1h`  → 12 个 **5 分钟桶**，覆盖近 1 小时
 * - `24h` → 24 个 **1 小时桶**，覆盖近 24 小时
 *
 * 桶的右边界对齐到当前时刻（见 `floorToBucket`）。曲线形态按
 * 「夜间低谷 / 早高峰 / 午间小峰 / 晚间主峰」的作息走——大屏上的曲线要
 * 一眼看出是人的作息，纯随机的锯齿看着像噪声，不像流量。
 *
 * @param {'1h'|'24h'} range
 * @returns {{stat_time: string, metric_value: number}[]} 升序，最后一个桶是当前桶
 */
export function buildTrend(range) {
  const isDay = range === '24h'
  const bucketMinutes = isDay ? 60 : 5
  const buckets = isDay ? 24 : 12

  // 结尾对齐到当前桶
  const end = floorToBucket(new Date(), bucketMinutes)
  const rows = []

  for (let i = buckets - 1; i >= 0; i--) {
    const t = new Date(end.getTime() - i * bucketMinutes * 60 * 1000)
    const hour = t.getHours()

    // 作息权重：0–6 点低谷，7–9 早高峰，12–13 午间小峰，19–22 晚间主峰
    let base
    if (hour >= 0 && hour < 6) base = 0.18
    else if (hour < 9) base = 0.72
    else if (hour < 12) base = 0.58
    else if (hour < 14) base = 0.66
    else if (hour < 19) base = 0.52
    else if (hour < 23) base = 1.0
    else base = 0.34

    // 桶容量：5 分钟桶与 1 小时桶的量级差 12 倍
    const capacity = isDay ? 1400 : 118
    // 噪声按绝对时间取种子，保证同一个桶每次刷新落在同一个值上
    const n = noise(Math.floor(t.getTime() / 60000))

    rows.push({
      stat_time: formatDateTime(t),
      metric_value: Math.round(capacity * base * (0.82 + n * 0.36))
    })
  }

  return rows
}

/**
 * 24 小时时段热力图（板块③）。
 *
 * 纵轴 = 近 7 天，横轴 = 0–23 时，值 = 该小时的事件数。
 *
 * ⚠️ **今天那一行只填到「当前小时」，之后的小时留空（`null`）。**
 *    不补 0 的理由与留存曲线一致：今天 22 点还没到，22 点的事件数是
 *    「未知」不是「0」。补 0 会让今天那一行的右半段变成一条深色，
 *    看起来像「今天下午之后流量突然归零」——一个不存在的异常。
 *    ECharts 的 heatmap 对 `null` 不绘制该格子，正好表达「还没到」。
 *
 * 返回扁平数组，`value = [hourIndex, dayIndex, count|null]`——
 * heatmap 系列要的就是这个形状，不在图表配置里现拼。
 */
export function buildHourlyHeatmap() {
  const days = 7
  const series = []
  const now = new Date()
  const todayHour = now.getHours()

  for (let d = 0; d < days; d++) {
    // ⚠️ d = 0 是**6 天前**，d = 6 是**今天**——与 `buildHeatmapDayLabels()`
    //    返回的标签数组同序（该数组也是从 6 天前排到今天）。
    //    两者顺序必须一致：ECharts 的 heatmap 按索引对齐纵轴标签，
    //    顺序错位不会报错，只会把每一天的数据画到另一个日期上。
    //    纵轴默认第 0 项在最下方，故今天自然落在最上面。
    const dayOffset = days - 1 - d
    const day = new Date(now.getTime() - dayOffset * 24 * 3600 * 1000)
    const isToday = dayOffset === 0

    for (let h = 0; h < 24; h++) {
      if (isToday && h > todayHour) {
        series.push([h, d, null]) // 还没到，不是 0
        continue
      }

      let base
      if (h < 6) base = 0.14
      else if (h < 9) base = 0.7
      else if (h < 12) base = 0.56
      else if (h < 14) base = 0.64
      else if (h < 19) base = 0.5
      else if (h < 23) base = 1.0
      else base = 0.32

      // 周末整体略高
      const weekend = day.getDay() === 0 || day.getDay() === 6 ? 1.15 : 1
      const n = noise(day.getDate() * 100 + h)

      series.push([h, d, Math.round(96 * base * weekend * (0.8 + n * 0.4))])
    }
  }

  return series
}

/** 热力图纵轴的日期标签（与 `buildHourlyHeatmap` 的 dayIndex 一一对应）。 */
export function buildHeatmapDayLabels() {
  const now = new Date()
  const labels = []
  for (let d = 6; d >= 0; d--) {
    const day = new Date(now.getTime() - d * 24 * 3600 * 1000)
    labels.push(`${day.getMonth() + 1}/${day.getDate()}`)
  }
  return labels
}

/**
 * 判脏规则命中数（子页③）。
 *
 * ⚠️ 七条规则的命中数**合计必须等于 834**，也就是子页①里「判脏」那个数。
 *    两处对不上就说明有一处漏算了规则。见 `mock/pipeline.js` 的说明。
 */
export const mockRuleHits = {
  MISSING_REQUIRED: 209, // 2.0% × 10420 ≈ 209
  ENUM_OUT_OF_RANGE: 157, // 1.5%
  TYPE_ERROR: 104, // 1.0%
  TIME_ILLEGAL: 104, // 1.0%
  ID_FORMAT_ERROR: 104, // 1.0%
  DUPLICATE: 104, // 1.0%
  OVERLONG_GARBLED: 52 // 0.5%
}

/** 兜底导出，供调试时确认七条规则的名字没写错。 */
export const RULE_KEYS_FOR_DEBUG = Object.keys(mockRuleHits)
