/**
 * 大屏图表的 ECharts option 构造函数。
 *
 * 为什么要把 option 从 `.vue` 里抽出来：
 *   一个 1080p 大屏上挤着十来个图，若 option 都写在模板对应的 setup 里，
 *   一个文件会到六七百行——里面混着「取数」「配色」「摆位置」三种关注点，
 *   改一个配色要在一堆 `onMounted` 之间翻。抽成**纯函数**之后：
 *     · `api/*.js`  只管取数与分组
 *     · 本文件       只管「数据 → option」
 *     · `.vue`      只管「option → 摆在哪一格」
 *   本文件里没有任何 `ref` / 响应式 / 副作用，全是输入输出的纯映射。
 *
 * 配色统一在下方 `THEME` 里。大屏用暗色，与营业厅的亮色完全不同，
 * 但**不用换一套变量名**——`styles/main.css` 的 `--tp-*` 是营业厅的，
 * 大屏自成一域，见 `ScreenLayout.vue`。
 */

import { EVENT_CATEGORY_LABEL, FUNNEL_STEP } from './constants'

/**
 * 大屏配色与通用样式。
 *
 * 轴、网格线一律用**半透明的浅蓝**而不是实色：大屏背景是深蓝渐变，
 * 半透明线能吸到背景色，过渡自然；写死 `#1e3a5f` 这种实色，
 * 一旦背景渐变调整，轴线就成了贴在上面的一块死色。
 */
export const THEME = {
  text: '#c8d6f0',
  textSub: '#7b8db5',
  axisLine: 'rgba(120, 160, 220, 0.22)',
  splitLine: 'rgba(120, 160, 220, 0.08)',
  accent: '#3d8bff',
  accent2: '#22c1a4',
  warn: '#f5a524',
  danger: '#ff5c8a',
  /** 地图无数据省份的填充色——必须比背景亮一点点，否则中国版图会缺一大块 */
  mapEmpty: '#12294d',
  mapBorder: 'rgba(90, 150, 230, 0.38)',
  /** 地图数据色阶，由浅到深 */
  mapRamp: ['#1b3f73', '#2a6fd6', '#4f9bff', '#8dc0ff']
}

/** 全局字体。大屏字号整体放大一档，远看才读得清。 */
const FONT = "'Microsoft YaHei', 'PingFang SC', sans-serif"

const baseTextStyle = {
  color: THEME.text,
  fontFamily: FONT,
  fontSize: 13
}

/** 每个 option 都要挂的基础件，避免逐处重复。 */
function base(extra) {
  return {
    textStyle: baseTextStyle,
    animationDuration: 600,
    ...extra
  }
}

/* ------------------------------------------------------------------ *
 *  板块②：实时趋势与热度                                                 *
 * ------------------------------------------------------------------ */

/**
 * 访问趋势曲线（折线 + 面积渐变）。
 *
 * `stat_time` 里只取 `HH:mm` 做横轴标签——完整日期在 5 分钟粒度的轴上
 * 会挤成一团，且同一天的曲线不需要重复标注日期。
 */
export function trendOption(rows, { range }) {
  const times = rows.map((row) => row.stat_time.slice(11, 16))
  const values = rows.map((row) => row.metric_value)

  return base({
    grid: { left: 44, right: 16, top: 24, bottom: 26 },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(8, 22, 46, 0.92)',
      borderColor: THEME.axisLine,
      textStyle: { color: THEME.text, fontSize: 12 },
      valueFormatter: (v) => `${v} 次`
    },
    xAxis: {
      type: 'category',
      data: times,
      boundaryGap: false,
      axisLine: { lineStyle: { color: THEME.axisLine } },
      axisTick: { show: false },
      axisLabel: {
        color: THEME.textSub,
        fontSize: 11,
        // 24 小时视图的点比 1 小时视图密，隔一个显示一个，否则标签会重叠
        interval: range === '24h' ? 2 : 0
      }
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: THEME.textSub, fontSize: 11 },
      splitLine: { lineStyle: { color: THEME.splitLine } }
    },
    series: [
      {
        type: 'line',
        smooth: true,
        showSymbol: false,
        data: values,
        lineStyle: { width: 2, color: THEME.accent },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(61, 139, 255, 0.42)' },
              { offset: 1, color: 'rgba(61, 139, 255, 0.02)' }
            ]
          }
        }
      }
    ]
  })
}

/**
 * 行为类型分布（环形图）。
 *
 * ⚠️ 扇区**按 `EVENT_CATEGORY_LABEL` 的顺序**排，不按数值大小排。
 *    两个理由是叠加的：
 *      1. 顺序固定，同一块扇区每次刷新都在同一位置，看惯了就认得；
 *         按数值排的话数据一变扇区就换位，大屏上颜色乱跳。
 *      2. 数据里缺了某一类时，固定顺序能让你**看出缺的是哪一个**；
 *         按数值排则只会少一块，不知道少的是谁。
 */
export function categoryRingOption(rows) {
  const map = {}
  rows.forEach((row) => {
    map[row.category] = row.metric_value
  })

  const data = Object.entries(EVENT_CATEGORY_LABEL).map(([code, meta]) => ({
    name: meta.label,
    value: map[code] ?? 0,
    itemStyle: { color: meta.color }
  }))

  return base({
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(8, 22, 46, 0.92)',
      borderColor: THEME.axisLine,
      textStyle: { color: THEME.text, fontSize: 12 },
      formatter: '{b}<br/>{c} 次（{d}%）'
    },
    legend: {
      bottom: 0,
      itemWidth: 8,
      itemHeight: 8,
      icon: 'circle',
      textStyle: { color: THEME.textSub, fontSize: 11 },
      // 六项在窄面板里一行放不下，换两行
      itemGap: 10
    },
    series: [
      {
        type: 'pie',
        radius: ['42%', '66%'],
        center: ['50%', '44%'],
        avoidLabelOverlap: true,
        label: { show: false },
        labelLine: { show: false },
        data
      }
    ]
  })
}

/**
 * 页面热度 Top N（横向条形图）。
 *
 * ⚠️ `yAxis.inverse: true`——ECharts 的类目轴默认第 0 项在最**下**，
 *    不反转的话「Top 1」会画在最底下，条形图就成了倒序。这一项漏了
 *    图不会报错、数据也没错，只是第一名排在最后一名下面。
 *
 * ⚠️ `nameOf` 是**回调**，把页面路径换成中文名。路径在中立层
 *    （`api/screen.js`）保持原样不动——真实后端返回的就是路径，
 *    翻译成什么字是**展示层**的事，不该混进取数逻辑。调用方从路由表取
 *    `meta.title`，见 `Screen.vue`。
 */
export function pageHeatOption(rows, nameOf) {
  // 横向条形图按值升序给 yAxis——配合 inverse 之后，最大值落在最上面
  const sorted = [...rows].sort((a, b) => a.metric_value - b.metric_value)

  return base({
    grid: { left: 92, right: 40, top: 10, bottom: 6 },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: 'rgba(8, 22, 46, 0.92)',
      borderColor: THEME.axisLine,
      textStyle: { color: THEME.text, fontSize: 12 },
      valueFormatter: (v) => `${v} 次`
    },
    xAxis: {
      type: 'value',
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: THEME.textSub, fontSize: 11 },
      splitLine: { lineStyle: { color: THEME.splitLine } }
    },
    yAxis: {
      type: 'category',
      inverse: true,
      data: sorted.map((row) => nameOf(row.page)),
      axisLine: { lineStyle: { color: THEME.axisLine } },
      axisTick: { show: false },
      axisLabel: { color: THEME.text, fontSize: 11 }
    },
    series: [
      {
        type: 'bar',
        data: sorted.map((row) => row.metric_value),
        barWidth: '58%',
        itemStyle: {
          borderRadius: [0, 3, 3, 0],
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 1,
            y2: 0,
            colorStops: [
              { offset: 0, color: 'rgba(61, 139, 255, 0.35)' },
              { offset: 1, color: THEME.accent }
            ]
          }
        },
        label: {
          show: true,
          position: 'right',
          color: THEME.textSub,
          fontSize: 11
        }
      }
    ]
  })
}

/* ------------------------------------------------------------------ *
 *  板块③：转化与活跃                                                     *
 * ------------------------------------------------------------------ */

/**
 * 行为漏斗。
 *
 * ⚠️ `sort: 'none'` 是**必须的**。ECharts 漏斗默认 `sort: 'descending'`，
 *    它会按数值把每一层重排——本项目的数据本来就是递减的，看起来一样；
 *    但一旦某段数据异常（比如登录数反而高于访问数），默认排序会把它
 *    **悄悄挪到下面去**，图还是漂亮的漏斗形状，错误被抹平了。
 *    固定 `'none'` 之后，异常会以「漏斗中间鼓出来」的形式直接显形。
 *
 * ⚠️ 层顺序取 `FUNNEL_STEP` 常量，不取 `Object.keys(数据)`。
 *    理由同判脏规则图：顺序是口径的一部分（见 constants.js）。
 */
export function funnelOption(map) {
  const colors = ['#2a6fd6', '#3d8bff', '#5aa4ff', '#8dc0ff']

  const data = FUNNEL_STEP.map((step, i) => ({
    name: step.label,
    value: map[step.code] ?? 0,
    itemStyle: { color: colors[i] ?? THEME.accent }
  }))

  return base({
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(8, 22, 46, 0.92)',
      borderColor: THEME.axisLine,
      textStyle: { color: THEME.text, fontSize: 12 },
      formatter: (p) => {
        const idx = FUNNEL_STEP.findIndex((s) => s.label === p.name)
        const first = data[0]?.value || 1
        const rate = ((p.value / first) * 100).toFixed(1)
        // 逐段转化率比总体占比更有用——看的是「哪一段掉得最狠」
        const prev = idx > 0 ? data[idx - 1].value : null
        const stepRate =
          prev && prev > 0 ? `本段转化 ${((p.value / prev) * 100).toFixed(1)}%<br/>` : ''
        return `${p.name}<br/>{c} 次<br/>${stepRate}整体转化 ${rate}%`
      }
    },
    series: [
      {
        type: 'funnel',
        left: '8%',
        right: '8%',
        top: 14,
        bottom: 14,
        minSize: '22%',
        maxSize: '100%',
        sort: 'none',
        gap: 2,
        label: {
          show: true,
          position: 'inside',
          color: '#fff',
          fontSize: 13,
          fontWeight: 500,
          formatter: '{b}'
        },
        labelLine: { show: false },
        data
      }
    ]
  })
}

/**
 * 24 小时时段热力图（近 7 天 × 24 小时）。
 *
 * ⚠️ `visualMap` 必须显式给 `min: 0`。默认会按数据的实际最小/最大值拉伸色阶，
 *    于是「全周最低 12 次」会被染成最浅色、看起来像 0——大屏上读者无从知道
 *    色阶端点是多少。锚定 0 之后，浅色就是真的低。
 *
 * ⚠️ `series` 里的 `null` 值 ECharts 不会绘制该格子。这是**有意利用**的：
 *    今天还没到的小时留白，读起来是「还没到」，补 0 的话会读成「流量归零」。
 */
export function heatmapOption({ days, series }) {
  const hours = Array.from({ length: 24 }, (_, i) => `${i}时`)

  return base({
    grid: { left: 48, right: 16, top: 12, bottom: 46 },
    tooltip: {
      position: 'top',
      backgroundColor: 'rgba(8, 22, 46, 0.92)',
      borderColor: THEME.axisLine,
      textStyle: { color: THEME.text, fontSize: 12 },
      formatter: (p) => {
        const [hour, day, value] = p.value
        if (value === null || value === undefined) {
          return `${days[day]} ${hours[hour]}<br/>尚未到达`
        }
        return `${days[day]} ${hours[hour]}<br/>${value} 次`
      }
    },
    xAxis: {
      type: 'category',
      data: hours,
      splitArea: { show: false },
      axisLine: { lineStyle: { color: THEME.axisLine } },
      axisTick: { show: false },
      axisLabel: { color: THEME.textSub, fontSize: 10, interval: 2 }
    },
    yAxis: {
      type: 'category',
      data: days,
      splitArea: { show: false },
      axisLine: { lineStyle: { color: THEME.axisLine } },
      axisTick: { show: false },
      axisLabel: { color: THEME.textSub, fontSize: 10 }
    },
    visualMap: {
      min: 0,
      max: 140,
      calculable: false,
      orient: 'horizontal',
      left: 'center',
      bottom: 4,
      itemWidth: 10,
      itemHeight: 70,
      textStyle: { color: THEME.textSub, fontSize: 10 },
      inRange: { color: ['#0f2444', '#1b3f73', '#2a6fd6', '#5aa4ff', '#9ecbff'] }
    },
    series: [
      {
        type: 'heatmap',
        data: series,
        label: { show: false },
        itemStyle: { borderColor: 'rgba(6, 18, 38, 0.9)', borderWidth: 1 },
        emphasis: { itemStyle: { borderColor: '#fff', borderWidth: 1 } }
      }
    ]
  })
}

/**
 * 用户留存曲线（次日 / 7 日）。
 *
 * ⚠️ 数据里的 `null` **原样传给 ECharts**，经过任何 `|| 0` 都会把
 *    「还没有数据」画成「留存为 0」。见 `api/screen.js#getRetention`。
 */
export function retentionOption({ dates, d1, d7 }) {
  const labels = dates.map((d) => d.slice(5)) // '2026-09-17' → '09-17'

  return base({
    grid: { left: 42, right: 16, top: 30, bottom: 26 },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(8, 22, 46, 0.92)',
      borderColor: THEME.axisLine,
      textStyle: { color: THEME.text, fontSize: 12 },
      // 缺口显示「—」而不是 0，读者一眼知道是没数据
      valueFormatter: (v) => (v === null || v === undefined ? '—' : `${v}%`)
    },
    legend: {
      top: 0,
      right: 0,
      itemWidth: 12,
      itemHeight: 2,
      textStyle: { color: THEME.textSub, fontSize: 11 }
    },
    xAxis: {
      type: 'category',
      data: labels,
      boundaryGap: false,
      axisLine: { lineStyle: { color: THEME.axisLine } },
      axisTick: { show: false },
      axisLabel: { color: THEME.textSub, fontSize: 10, interval: 2 }
    },
    yAxis: {
      type: 'value',
      name: '%',
      nameTextStyle: { color: THEME.textSub, fontSize: 10 },
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: THEME.textSub, fontSize: 10 },
      splitLine: { lineStyle: { color: THEME.splitLine } }
    },
    series: [
      {
        name: '次日留存',
        type: 'line',
        smooth: true,
        showSymbol: true,
        symbolSize: 5,
        // connectNulls 保持 false（默认）：缺口要断开，不能连过去把缺口藏掉
        data: d1,
        lineStyle: { width: 2, color: THEME.accent2 },
        itemStyle: { color: THEME.accent2 }
      },
      {
        name: '7 日留存',
        type: 'line',
        smooth: true,
        showSymbol: true,
        symbolSize: 5,
        data: d7,
        lineStyle: { width: 2, color: THEME.warn },
        itemStyle: { color: THEME.warn }
      }
    ]
  })
}

/* ------------------------------------------------------------------ *
 *  板块④：用户维度分布                                                   *
 * ------------------------------------------------------------------ */

/** 注册地图用的名字，注册与使用必须是同一个字符串。 */
export const MAP_NAME = 'china'

/**
 * 地域分布地图。
 *
 * ⚠️ 调用前必须已 `echarts.registerMap('china', geoJson)`，
 *    且 geoJson 的 `properties.name` 要与 `data[].name` 逐字一致。
 *    对不上的省份**不报错、不变色**——只是地图上那块没有数据。
 *    这是接地图时最常见的坑，见 `mock/screen.js` 的说明。
 *
 * ⚠️ `visualMap.max` 取数据最大值而不是写死。写死一个数，
 *    某天数据量级变了（造数脚本多造一批用户），整张地图会全变成最深色，
 *    地图就失去了意义而**不会报任何错**。
 */
export function regionMapOption(rows) {
  const max = rows.reduce((m, row) => Math.max(m, row.value), 0)

  return base({
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(8, 22, 46, 0.92)',
      borderColor: THEME.axisLine,
      textStyle: { color: THEME.text, fontSize: 12 },
      formatter: (p) =>
        p.value === undefined || Number.isNaN(p.value)
          ? `${p.name}<br/>暂无用户`
          : `${p.name}<br/>${p.value} 人`
    },
    visualMap: {
      min: 0,
      max: max || 1,
      left: 4,
      bottom: 10,
      itemWidth: 10,
      itemHeight: 62,
      text: ['多', '少'],
      textStyle: { color: THEME.textSub, fontSize: 10 },
      inRange: { color: THEME.mapRamp }
    },
    series: [
      {
        type: 'map',
        map: MAP_NAME,
        roam: false,
        zoom: 1.16,
        top: 8,
        bottom: 8,
        label: { show: false },
        itemStyle: {
          // 无数据省份的填充：比背景略亮，让版图轮廓完整
          areaColor: THEME.mapEmpty,
          borderColor: THEME.mapBorder,
          borderWidth: 0.6
        },
        emphasis: {
          label: { show: true, color: '#fff', fontSize: 10 },
          itemStyle: { areaColor: '#4f9bff' }
        },
        select: { disabled: true },
        data: rows
      }
    ]
  })
}

/**
 * 套餐分布（玫瑰图）。
 *
 * ⚠️ 用玫瑰图而非普通饼图：五个套餐的用户数差距不小，普通饼图里
 *    最小的那块看不出差别；玫瑰图的半径也编码数值，差异更直观。
 */
export function packagePieOption(rows) {
  const palette = ['#3d8bff', '#22c1a4', '#a06cff', '#f5a524', '#ff5c8a']

  return base({
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(8, 22, 46, 0.92)',
      borderColor: THEME.axisLine,
      textStyle: { color: THEME.text, fontSize: 12 },
      formatter: '{b}<br/>{c} 人（{d}%）'
    },
    legend: {
      bottom: 0,
      itemWidth: 8,
      itemHeight: 8,
      icon: 'circle',
      textStyle: { color: THEME.textSub, fontSize: 10 },
      itemGap: 8
    },
    series: [
      {
        type: 'pie',
        roseType: 'radius',
        radius: ['16%', '66%'],
        center: ['50%', '42%'],
        label: { show: false },
        labelLine: { show: false },
        itemStyle: { borderColor: 'rgba(6, 18, 38, 0.9)', borderWidth: 1 },
        data: rows.map((row, i) => ({
          name: row.name,
          value: row.value,
          itemStyle: { color: palette[i % palette.length] }
        }))
      }
    ]
  })
}

/**
 * 活跃度分层（柱状图）。
 *
 * ⚠️ 柱子固定三根（高 / 中 / 低），数据由 `api/screen.js#getActivityDist()`
 *    按固定顺序补齐——**某一层一个用户都没有时画成 0 高的柱子，而不是消失**。
 *    三层是「活跃度分层」这个口径的定义，不是数据的产物。
 */
export function activityBarOption(rows) {
  const palette = ['#22c1a4', '#3d8bff', '#7b8db5']

  return base({
    grid: { left: 44, right: 16, top: 22, bottom: 22 },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: 'rgba(8, 22, 46, 0.92)',
      borderColor: THEME.axisLine,
      textStyle: { color: THEME.text, fontSize: 12 },
      valueFormatter: (v) => `${v} 人`
    },
    xAxis: {
      type: 'category',
      data: rows.map((row) => row.name),
      axisLine: { lineStyle: { color: THEME.axisLine } },
      axisTick: { show: false },
      axisLabel: { color: THEME.textSub, fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: THEME.textSub, fontSize: 10 },
      splitLine: { lineStyle: { color: THEME.splitLine } }
    },
    series: [
      {
        type: 'bar',
        barWidth: '42%',
        data: rows.map((row, i) => ({
          value: row.value,
          itemStyle: {
            borderRadius: [4, 4, 0, 0],
            color: palette[i % palette.length]
          }
        })),
        label: {
          show: true,
          position: 'top',
          color: THEME.textSub,
          fontSize: 11
        }
      }
    ]
  })
}

/* ------------------------------------------------------------------ *
 *  子页 · 系统运行分析                                                   *
 * ------------------------------------------------------------------ */

/**
 * 脏数据占比趋势（折线）。
 *
 * ⚠️ `yAxis` 不锚定 0，也不写死范围。占比是**窄幅波动**的指标
 *    （本项目在 6%–9% 之间），锚定 0 会把整条线压成一条几乎水平的直线，
 *    看不出任何变化。让 ECharts 按数据自适应，波动才读得出来。
 *    —— 这与热力图必须锚定 `min: 0` 正好相反，两者是不同指标的正当选择：
 *    **热力图画的是「绝对量、可归零」，占比趋势画的是「在基准附近的偏移」**。
 */
export function dirtyRatioOption(rows) {
  const labels = rows.map((row) => row.stat_date.slice(5)) // '2026-09-30' → '09-30'
  const values = rows.map((row) => row.metric_value)

  return base({
    grid: { left: 42, right: 26, top: 24, bottom: 26 },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(8, 22, 46, 0.92)',
      borderColor: THEME.axisLine,
      textStyle: { color: THEME.text, fontSize: 12 },
      valueFormatter: (v) => `${v}%`
    },
    xAxis: {
      type: 'category',
      data: labels,
      boundaryGap: false,
      axisLine: { lineStyle: { color: THEME.axisLine } },
      axisTick: { show: false },
      axisLabel: { color: THEME.textSub, fontSize: 10, interval: rows.length > 10 ? 4 : 0 }
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: THEME.textSub, fontSize: 10, formatter: '{value}%' },
      splitLine: { lineStyle: { color: THEME.splitLine } }
    },
    series: [
      {
        type: 'line',
        smooth: true,
        showSymbol: rows.length <= 10,
        symbolSize: 5,
        data: values,
        lineStyle: { width: 2, color: THEME.danger },
        itemStyle: { color: THEME.danger },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(255, 92, 138, 0.32)' },
              { offset: 1, color: 'rgba(255, 92, 138, 0.02)' }
            ]
          }
        },
        // 目标线：注入比例定的是 8%，趋势围着它波动才说明链路稳定
        markLine: {
          silent: true,
          symbol: 'none',
          lineStyle: { color: 'rgba(245, 165, 36, 0.7)', type: 'dashed' },
          label: {
            formatter: '注入基线 8%',
            color: THEME.warn,
            fontSize: 10,
            position: 'insideEndTop'
          },
          data: [{ yAxis: 8 }]
        }
      }
    ]
  })
}

/**
 * 规则命中分布（横向条形图）。
 *
 * ⚠️ 七条规则的标签由**调用方从 `DIRTY_RULE` 常量给全**，本函数不做
 *    「数据里有几条画几条」的事。理由见 `api/pipeline.js#getRuleHits`。
 */
export function ruleHitsOption(rows, total) {
  const sorted = [...rows].sort((a, b) => a.value - b.value)

  return base({
    grid: { left: 116, right: 46, top: 10, bottom: 6 },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: 'rgba(8, 22, 46, 0.92)',
      borderColor: THEME.axisLine,
      textStyle: { color: THEME.text, fontSize: 12 },
      formatter: (params) => {
        const p = params[0]
        const pct = total > 0 ? ((p.value / total) * 100).toFixed(1) : '0.0'
        return `${p.name}<br/>${p.value} 条（占判脏 ${pct}%）`
      }
    },
    xAxis: {
      type: 'value',
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: THEME.textSub, fontSize: 10 },
      splitLine: { lineStyle: { color: THEME.splitLine } }
    },
    yAxis: {
      type: 'category',
      inverse: true,
      data: sorted.map((row) => row.label),
      axisLine: { lineStyle: { color: THEME.axisLine } },
      axisTick: { show: false },
      axisLabel: { color: THEME.text, fontSize: 11 }
    },
    series: [
      {
        type: 'bar',
        data: sorted.map((row) => row.value),
        barWidth: '52%',
        itemStyle: {
          borderRadius: [0, 3, 3, 0],
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 1,
            y2: 0,
            colorStops: [
              { offset: 0, color: 'rgba(255, 92, 138, 0.3)' },
              { offset: 1, color: THEME.danger }
            ]
          }
        },
        label: { show: true, position: 'right', color: THEME.textSub, fontSize: 11 }
      }
    ]
  })
}
