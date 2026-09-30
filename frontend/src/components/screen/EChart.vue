<script setup>
/**
 * ECharts 基座组件。大屏上十来个图全部走这一个组件。
 *
 * 三项刻意的设计：
 *
 * ① **按需引入**（`echarts/core` + 显式 `use`），不是 `import * as echarts
 *    from 'echarts'`。全量引入会把地图、雷达、树图、桑基图等本项目一个都
 *    用不到的图表和组件打进包里，而大屏是首屏——选型⑤ 对 Element Plus
 *    要求按需引入，这里同理。下方 `use()` 清单就是「本大屏实际用了什么」
 *    的准确答案，新增图表类型时要往这里加，否则运行时才报错
 *    （`Series xxx not exists`，报错信息不会提示你漏了 `use`）。
 *
 * ② **实例用 `shallowRef` 存，不用 `ref`。** ECharts 实例内部有大量互相
 *    引用的循环结构（实例 → 各组件 → 实例）。用 `ref` 会让 Vue 深度遍历
 *    整个对象图做响应式代理，大屏上十来个实例同时来这一遍，卡顿且毫无收益
 *    ——它本来就只是「一个外部对象句柄」，不需要被追踪。
 *
 * ③ **不监听 window resize。** 大屏用 CSS `transform: scale()` 整体缩放
 *    （决策「大屏适配倾向 transform: scale()」），容器的**逻辑像素尺寸
 *    永远是 1920×1080 不变**，变的只是渲染时的缩放倍率。所以图表不需要
 *    跟着窗口尺寸 resize——这正是选 scale 方案省下来的事。
 *    （代价：canvas 在非 100% 缩放下会被重采样，看得出一点点柔。
 *     1080p 显示器上倍率为 1，完全一致；这是选型时就接受的取舍。）
 */
import { onBeforeUnmount, onMounted, nextTick, ref, shallowRef, watch } from 'vue'
import * as echarts from 'echarts/core'
import {
  BarChart,
  FunnelChart,
  HeatmapChart,
  LineChart,
  MapChart,
  PieChart
} from 'echarts/charts'
import {
  GridComponent,
  LegendComponent,
  MarkLineComponent,
  TooltipComponent,
  VisualMapComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([
  // 图表类型
  LineChart,
  BarChart,
  PieChart,
  FunnelChart,
  HeatmapChart,
  MapChart,
  // 组件
  GridComponent,
  TooltipComponent,
  LegendComponent,
  VisualMapComponent,
  // `markLine` 不在 GridComponent 里，是**独立组件**（脏数据趋势的 8% 基线要用）
  MarkLineComponent,
  // 渲染器：Canvas 足够，不用 SVG
  CanvasRenderer
])

const props = defineProps({
  /** 完整的 ECharts option。为 `null` 时不绘制（数据还没到）。 */
  option: { type: Object, default: null }
})

const el = ref(null)
const chart = shallowRef(null)

onMounted(async () => {
  if (!el.value) return
  chart.value = echarts.init(el.value, null, { renderer: 'canvas' })
  if (props.option) {
    chart.value.setOption(props.option)
  }
  // 初始化时容器可能还没拿到最终尺寸（父级是 flex 布局，要等一次布局完成），
  // 这时 ECharts 会按 0×0 或旧尺寸画，之后不会自己纠正。补一次 resize。
  await nextTick()
  chart.value?.resize()
})

/**
 * ⚠️ 用**合并**模式（`setOption(option)`，不传 `notMerge: true`）。
 *
 *    实时指标每 2 秒推一次，若每次都整份重建 option，图表会整体重播入场动画
 *    ——大屏上表现为「每两秒闪一下」。合并模式下 ECharts 只更新变化的数值，
 *    数字是平滑变的。
 *
 *    合并模式唯一的风险是「旧 series 残留」（新旧系列数量不同时会留下旧系列）。
 *    本大屏所有图表的系列数量都是**恒定**的（趋势 1 条、留存 2 条、
 *    漏斗 1 个……），切换趋势的 1 小时/24 小时只是数据点数变化，
 *    系列数不变，所以合并是安全的。
 */
watch(
  () => props.option,
  (next) => {
    if (!next || !chart.value) return
    chart.value.setOption(next)
  },
  { deep: false }
)

onBeforeUnmount(() => {
  // 不 dispose 会泄漏：每次进出大屏都新建一个实例，旧的还挂在 DOM 上
  // 持有 canvas 与定时器（ECharts 内部有动画帧循环）。
  chart.value?.dispose()
  chart.value = null
})
</script>

<template>
  <div ref="el" class="echart"></div>
</template>

<style scoped>
.echart {
  width: 100%;
  height: 100%;
  /* 图表在窄面板里最小也要占满，父级 panel 已经限定了高度 */
  min-height: 0;
}
</style>
