<script setup>
/**
 * 分析平台 · 主页 · 用户行为分析（实时大屏四板块）。
 *
 * 四板块构成见 claude.md 决策「大屏板块构成为四板块全选」：
 *   ① 实时核心指标区（实时链路）
 *   ② 实时趋势与热度（实时链路）
 *   ③ 转化与活跃（漏斗与留存为离线链路；时段热力图可实时）
 *   ④ 用户维度分布（离线链路）
 *
 * ⚠️ **本页不上报任何埋点。**
 *    《开发文档》的角色表写明运营分析员「不产生行为数据」，且
 *    决策「统一日志规范」列出的十二个事件里**没有任何一个属于分析平台**。
 *    这不是遗漏，是刻意的：若给大屏加一个 `view_screen`，运营分析员看大屏
 *    的行为就会进行为日志，而日志又是大屏的分析对象——**系统开始观察自己**，
 *    指标会被这个自我观察污染。路由表里这两条路由也都没有 `meta.track`。
 *
 * ⚠️ 本页**不用任何 Element Plus 组件**（选型⑤）。切换按钮、下拉都是自绘。
 */

import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import ScreenLayout from '@/layouts/ScreenLayout.vue'
import PanelBox from '@/components/screen/PanelBox.vue'
import StatCard from '@/components/screen/StatCard.vue'
import EChart from '@/components/screen/EChart.vue'
import {
  getRealtimeSnapshot,
  subscribeRealtime,
  toCounterMap,
  getTrend,
  getPageHeat,
  getCategoryDist,
  getFunnel,
  getRetention,
  getHourlyHeatmap,
  getRegionDist,
  getPackageDist,
  getActivityDist
} from '@/api/screen'
import {
  trendOption,
  categoryRingOption,
  pageHeatOption,
  funnelOption,
  heatmapOption,
  retentionOption,
  regionMapOption,
  packagePieOption,
  activityBarOption
} from '@/utils/screenCharts'
import { ensureChinaMap } from '@/utils/chinaMap'
import { floorToBucket, formatDateTime } from '@/utils/datetime'

const router = useRouter()

/* ---------------- ① 实时核心指标 ---------------- */

/**
 * `metric_code → 当前值`。
 * 收到快照前是空对象，数字卡会显示 `--` 而不是 0（见 `StatCard.vue`）。
 */
const counters = reactive({})

const statCards = [
  { code: 'online_users', label: '当前在线用户', unit: '人', accent: '#4fd1e0' },
  { code: 'today_pv', label: '今日实时 PV', unit: '次', accent: '#3d8bff' },
  { code: 'today_uv', label: '今日实时 UV', unit: '人', accent: '#a06cff' },
  { code: 'today_orders', label: '今日办理量', unit: '单', accent: '#ff5c8a' },
  { code: 'today_tickets', label: '今日新增工单', unit: '单', accent: '#f5a524' }
]

/* ---------------- ② 实时趋势与热度 ---------------- */

const trendRange = ref('1h')
const trendRows = ref([])
const pageHeatRows = ref([])
const categoryRows = ref([])

/** 趋势曲线每个桶的分钟数——与大屏副标题里写的「近 1 小时 / 24 小时」对应。 */
const BUCKET_MINUTES = { '1h': 5, '24h': 60 }

/**
 * 页面路径 → 中文名。
 *
 * ⚠️ 从**路由表**取 `meta.title`，不在这里写一份 `{ '/user/bills': '账单查询' }`
 *    的映射表。写映射表的后果是：路由改名、加页面时这里不会报错，
 *    只是那一行在图上**退回显示英文路径**（或者干脆对不上被丢掉）。
 *    从路由表取，页面一加就自动有了标签，两份数据不可能漂移。
 *
 * 未匹配的路径（比如接口里冒出一条已下线的旧路径）回退显示原路径——
 * 显示原始值总比显示空白强，至少能看出是哪个页面。
 */
function pageName(path) {
  const resolved = router.resolve(path)
  return resolved?.meta?.title || path
}

/* ---------------- ③ 转化与活跃 ---------------- */

const funnelMap = ref({})
const heatmapData = ref({ days: [], series: [] })
const retentionData = ref({ dates: [], d1: [], d7: [] })

/* ---------------- ④ 用户维度分布 ---------------- */

const regionRows = ref([])
const packageRows = ref([])
const activityRows = ref([])

/**
 * 中国地图的 geoJSON 是否已注册进 ECharts。
 *
 * ⚠️ 这不是「加载中」的装饰状态，是**必须挡住崩溃**的开关。
 *    ECharts 的 `map` 系列在 setOption 时就要解析地图几何：
 *    `geoSourceManager.load()` 找不到已注册的地图会**返回 undefined**，
 *    紧接着 `MapSeries.getInitialData` 读 `geoSource.regions` 直接抛
 *    `Cannot read properties of undefined (reading 'regions')`。
 *    注意它**不是**「画个空白图」那么客气——抛在 SeriesModel.init 里，
 *    整个 setOption 挂掉，该图的 canvas 根本不会建出来。
 *
 *    所以「数据到了」与「地图注册好了」是两件事，两者都满足才能出 option。
 *    只判断数据的话，注册那 582 KB 的这段时间里必然崩——而且崩得挑时候：
 *    本地是快的，加载慢的机器或线上才现形。
 */
const mapReady = ref(false)

/* ---------------- 图表 option（都是 computed，数据一变图就变） ---------------- */

const trendChartOption = computed(() =>
  trendRows.value.length ? trendOption(trendRows.value, { range: trendRange.value }) : null
)
const categoryChartOption = computed(() =>
  categoryRows.value.length ? categoryRingOption(categoryRows.value) : null
)
const pageHeatChartOption = computed(() =>
  pageHeatRows.value.length ? pageHeatOption(pageHeatRows.value, pageName) : null
)
const funnelChartOption = computed(() =>
  Object.keys(funnelMap.value).length ? funnelOption(funnelMap.value) : null
)
const heatmapChartOption = computed(() =>
  heatmapData.value.series.length ? heatmapOption(heatmapData.value) : null
)
const retentionChartOption = computed(() =>
  retentionData.value.dates.length ? retentionOption(retentionData.value) : null
)
const regionChartOption = computed(() =>
  // 两个条件缺一不可：没有数据是空图，没有地图**是崩溃**（见 mapReady 说明）。
  mapReady.value && regionRows.value.length ? regionMapOption(regionRows.value) : null
)
const packageChartOption = computed(() =>
  packageRows.value.length ? packagePieOption(packageRows.value) : null
)
const activityChartOption = computed(() =>
  activityRows.value.length ? activityBarOption(activityRows.value) : null
)

/* ---------------- 趋势曲线的实时增量 ---------------- */

/**
 * 累计 PV 的上一次读数，用来算增量。
 *
 * ⚠️ 趋势曲线的最后一个桶 = **当前时间桶内累计的 PV**。SSE 推的
 *    `today_pv` 是全天累计值，所以「本桶的 PV」要靠**两次读数的差**得到：
 *    全天累计涨了 3，就意味着刚刚这一瞬间发生了 3 次访问，落在当前桶里。
 *
 *    这是唯一说得通的算法。若图省事把全天累计值直接画到最后一个桶上，
 *    曲线尾部会飙到一万八，而其余桶只有几十——图上看起来像「刚才流量爆炸」。
 */
let lastPv = null

/** 当前趋势数据的桶键（`YYYY-MM-DD HH:mm:00`），用来判断是否跨桶。 */
function bucketKey(text, minutes) {
  // ⚠️ 这里出现了 `new Date(...)`，与「全站用定宽字符串比较时间」的约定
  //    不冲突——**只用于桶对齐的算术**，算完立刻格式回字符串，
  //    全程不与另一条字符串做比较。约定约束的是比较，不是计算。
  const d = new Date(String(text).replace(' ', 'T'))
  return formatDateTime(floorToBucket(d, minutes))
}

function applyRealtime(record) {
  counters[record.metric_code] = record.metric_value

  if (record.metric_code !== 'today_pv') return

  const delta = lastPv === null ? 0 : record.metric_value - lastPv
  lastPv = record.metric_value

  const rows = trendRows.value
  if (!rows.length) return

  const minutes = BUCKET_MINUTES[trendRange.value] || 5
  const last = rows[rows.length - 1]
  const incoming = bucketKey(record.stat_time, minutes)

  if (incoming === bucketKey(last.stat_time, minutes)) {
    // 还在同一个桶里：把增量累加进去
    if (delta === 0) return
    rows[rows.length - 1] = { ...last, metric_value: last.metric_value + delta }
  } else {
    // 跨桶了：窗口整体左移一格，新桶从这次的增量开始
    // （`slice(1)` 丢掉最老的一桶，长度保持不变——这是滚动窗口的定义）
    rows.push({ stat_time: incoming, metric_value: Math.max(delta, 0) })
    rows.shift()
    trendRows.value = [...rows]
    return
  }

  trendRows.value = [...rows]
}

/* ---------------- 订阅生命周期 ---------------- */

let subscription = null

onMounted(async () => {
  // 并行取数：十来个请求串行做的话，大屏要一项一项地「长出来」。
  const [snapshot, trend, heat, category, funnel, heatmap, retention] = await Promise.all([
    getRealtimeSnapshot(),
    getTrend(trendRange.value),
    getPageHeat(10),
    getCategoryDist(),
    getFunnel(),
    getHourlyHeatmap(),
    getRetention()
  ])

  Object.assign(counters, toCounterMap(snapshot))
  trendRows.value = trend
  // 快照到位后记下 PV 基线，**必须在拿到初值之后**——否则 SSE 推来第一个
  // `today_pv` 时 `lastPv` 还是 null，算不出增量，最后一个桶会一直是 0。
  lastPv = counters.today_pv ?? null
  pageHeatRows.value = heat
  categoryRows.value = category
  funnelMap.value = funnel
  heatmapData.value = heatmap
  retentionData.value = retention

  // 板块④是三块离线指标，单独一批——它们不影响首屏实时部分的呈现速度
  const [region, packages, activity] = await Promise.all([
    getRegionDist(),
    getPackageDist(),
    getActivityDist()
  ])
  regionRows.value = region
  packageRows.value = packages
  activityRows.value = activity

  // ⚠️ 地图必须**先注册再画**，且注册是异步的（要拉 582 KB 的 geoJSON）。
  //    与上面那批并行发出去即可，不必串行等——真正的守卫是 `mapReady`：
  //    数据与地图谁先到都无所谓，`regionChartOption` 两个条件都满足才出值，
  //    EChart 组件收到 null 不绘制，注册完成后自动补上。
  //
  //    catch 里**必须往控制台留声**，不能吞。失败时「地域分布」会一直空着，
  //    和「后端没返数据」长得一模一样；不打印的话，这个图上永远少一块，
  //    而没有任何地方说明原因。
  ensureChinaMap()
    .then(() => {
      mapReady.value = true
    })
    .catch((err) => {
      console.error('[大屏] 地图加载失败，地域分布将不显示：', err)
    })

  // 订上实时推送。SSE 的初值要单独拉（见 api/realtime.js），所以顺序是
  // 「先取快照、再订阅」，反过来的话第一帧会拿不到基线。
  subscription = subscribeRealtime(applyRealtime)
})

onBeforeUnmount(() => {
  // ⚠️ 不关订阅的后果：离开大屏后定时器还在跑，回调还在改一个已经卸载的
  //    组件的响应式状态——在 Vue 里不报错，只是白跑，且每次进出都多一个。
  subscription?.close()
  subscription = null
})

/** 切换趋势范围时重新取数。窗口长度变了，必须整条重取，不能在前端截。 */
watch(trendRange, async (range) => {
  trendRows.value = await getTrend(range)
})
</script>

<template>
  <ScreenLayout
    title="电信用户行为分析系统"
    subtitle="实时大屏 · 用户在系统里做了什么"
  >
    <template #actions>
      <router-link class="screen-link" to="/screen/pipeline">
        系统运行分析 →
      </router-link>
    </template>

    <!-- ① 实时核心指标区 -->
    <div class="screen-row screen-row--stats">
      <StatCard
        v-for="card in statCards"
        :key="card.code"
        :label="card.label"
        :value="counters[card.code] ?? null"
        :unit="card.unit"
        :accent="card.accent"
      />
    </div>

    <!-- ② 实时趋势与热度 -->
    <div class="screen-row screen-row--charts">
      <PanelBox title="访问趋势" link="REALTIME" class="screen-cell screen-cell--wide">
        <template #extra>
          <div class="switcher">
            <button
              v-for="r in ['1h', '24h']"
              :key="r"
              class="switcher__btn"
              :class="{ 'is-active': trendRange === r }"
              type="button"
              @click="trendRange = r"
            >
              {{ r === '1h' ? '近 1 小时' : '近 24 小时' }}
            </button>
          </div>
        </template>
        <EChart :option="trendChartOption" />
      </PanelBox>

      <PanelBox title="行为类型分布" link="REALTIME" class="screen-cell">
        <EChart :option="categoryChartOption" />
      </PanelBox>

      <PanelBox title="页面热度 Top 10" link="REALTIME" class="screen-cell">
        <EChart :option="pageHeatChartOption" />
      </PanelBox>
    </div>

    <!-- ③ 转化与活跃 -->
    <div class="screen-row screen-row--charts">
      <PanelBox title="行为漏斗（访问 → 登录 → 查询 → 办理）" link="OFFLINE" class="screen-cell">
        <EChart :option="funnelChartOption" />
      </PanelBox>

      <PanelBox title="24 小时时段热力图" link="REALTIME" class="screen-cell screen-cell--wide">
        <EChart :option="heatmapChartOption" />
      </PanelBox>

      <PanelBox title="用户留存曲线" link="OFFLINE" class="screen-cell">
        <EChart :option="retentionChartOption" />
      </PanelBox>
    </div>

    <!-- ④ 用户维度分布 -->
    <div class="screen-row screen-row--charts">
      <PanelBox title="地域分布" link="OFFLINE" class="screen-cell screen-cell--wide">
        <EChart :option="regionChartOption" />
      </PanelBox>

      <PanelBox title="套餐分布" link="OFFLINE" class="screen-cell">
        <EChart :option="packageChartOption" />
      </PanelBox>

      <PanelBox title="用户活跃度分层" link="OFFLINE" class="screen-cell">
        <EChart :option="activityChartOption" />
      </PanelBox>
    </div>
  </ScreenLayout>
</template>

<style scoped>
.screen-link {
  padding: 5px 14px;
  border: 1px solid rgba(90, 164, 255, 0.45);
  border-radius: 16px;
  background: rgba(61, 139, 255, 0.12);
  color: #9ecbff;
  font-size: 12px;
  letter-spacing: 0.5px;
  transition: all 0.2s;
}

.screen-link:hover {
  border-color: #5aa4ff;
  background: rgba(61, 139, 255, 0.25);
  color: #eaf2ff;
}

.screen-row {
  display: flex;
  gap: 12px;
}

/* ① 数字卡一行五张，高度固定——它们是「一屏概览」，不该抢图表的空间 */
.screen-row--stats {
  flex: none;
  height: 84px;
}

/* ②③④ 三行平分剩余高度 */
.screen-row--charts {
  flex: 1;
  min-height: 0;
}

.screen-cell {
  /* 三列等分的基准：flex:1 + min-width:0 缺一不可。
     `min-width: 0` 是让 flex 子项**允许被压缩到内容宽度以下**——
     少了它，ECharts 画布撑出的宽度会把这一格顶开，三列不再等分，
     而这是个大屏上很显眼、但排查时完全想不到是 flex 默认值引起的问题。 */
  flex: 1;
  min-width: 0;
  min-height: 0;
}

/* 中间一列稍宽：热力图与地图都是「铺开才好看」的图 */
.screen-cell--wide {
  flex: 1.35;
}

.switcher {
  display: flex;
  gap: 2px;
  padding: 2px;
  border-radius: 4px;
  background: rgba(10, 26, 52, 0.8);
}

.switcher__btn {
  padding: 2px 9px;
  border: none;
  border-radius: 3px;
  background: transparent;
  color: #7b8db5;
  font-family: inherit;
  font-size: 11px;
  cursor: pointer;
  transition: all 0.2s;
}

.switcher__btn.is-active {
  background: rgba(61, 139, 255, 0.35);
  color: #eaf2ff;
}

.switcher__btn:hover:not(.is-active) {
  color: #c8d6f0;
}
</style>
