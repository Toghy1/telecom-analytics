<script setup>
/**
 * 分析平台 · 离线数据分析（三个标签页的第二页）。
 *
 * 2026-10-21 由 `Screen.vue` 拆出。本页 = 板块③（漏斗、留存）+ 板块④
 * （地域、套餐、活跃度），即原主页里所有 `link="OFFLINE"` 的图。
 *
 * 这些指标全部**必须全量重算**，落 `dws_metric_offline`，由离线 ETL / Spark
 * 回填——正是「指标分工原则」里划给离线链路的那一半。因此本页**没有 SSE**：
 * 决策「大屏刷新采用 SSE 推送」写明离线指标加载一次不刷新，一天更新一次的数
 * 做推送是纯浪费。这也是本页与实时大屏在实现上唯一的结构性差别。
 *
 * ⚠️ **本页不上报任何埋点**，理由同 `Realtime.vue`（角色表 + 十二个事件里
 *    没有任何一个属于分析平台）。路由表也不给本页 `meta.track`。
 *
 * ⚠️ 本页**不用任何 Element Plus 组件**（选型⑤）。
 */

import { computed, onMounted, ref } from 'vue'
import ScreenLayout from '@/layouts/ScreenLayout.vue'
import PanelBox from '@/components/screen/PanelBox.vue'
import EChart from '@/components/screen/EChart.vue'
import {
  getFunnel,
  getRetention,
  getRegionDist,
  getPackageDist,
  getActivityDist
} from '@/api/screen'
import {
  funnelOption,
  retentionOption,
  regionMapOption,
  packagePieOption,
  activityBarOption
} from '@/utils/screenCharts'
import { ensureChinaMap } from '@/utils/chinaMap'

/* ---------------- ③ 转化与活跃 ---------------- */

const funnelMap = ref({})
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
 *
 *    ⚠️ 拆分后这条守卫**只在本页需要**：地图 582 KB 由浏览器按需 fetch，
 *    放实时大屏那边等于让它白等一份用不上的 geoJSON。
 */
const mapReady = ref(false)

/* ---------------- 图表 option（都是 computed，数据一变图就变） ---------------- */

const funnelChartOption = computed(() =>
  Object.keys(funnelMap.value).length ? funnelOption(funnelMap.value) : null
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

/* ---------------- 取数 ---------------- */

onMounted(async () => {
  // 五块离线指标彼此独立，一次并行发出去。
  const [funnel, retention, region, packages, activity] = await Promise.all([
    getFunnel(),
    getRetention(),
    getRegionDist(),
    getPackageDist(),
    getActivityDist()
  ])

  funnelMap.value = funnel
  retentionData.value = retention
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
})
</script>

<template>
  <ScreenLayout
    title="电信用户行为分析系统"
    subtitle="离线链路 · 用户在系统里做了什么"
  >
    <!-- ③ 转化与活跃 -->
    <div class="screen-row screen-row--charts">
      <PanelBox title="行为漏斗（访问 → 登录 → 查询 → 办理）" link="OFFLINE" class="screen-cell">
        <EChart :option="funnelChartOption" />
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
.screen-row {
  display: flex;
  gap: 12px;
}

/* 两行平分剩余高度 */
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

/* 中间一列稍宽：地图是「铺开才好看」的图 */
.screen-cell--wide {
  flex: 1.35;
}
</style>
