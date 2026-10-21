<script setup>
/**
 * 分析平台 · 子页 · 系统运行分析。
 *
 * 回答的问题是「系统本身跑得怎么样」——与主页「用户在系统里做了什么」
 * 是两回事（决策「分析平台采用『主页 + 子页』结构」）。四块内容：
 *   ① 今日管道量  ② 脏数据占比趋势  ③ 规则命中分布  ④ 死信明细
 *
 * 为什么这一页必须存在：**脏数据过滤是验收必考环节，而主页四个板块里
 * 没有任何一处展示脏数据**——四板块展示的全是业务分析结果，
 * 清洗是整条流水线上唯一没有可视化证据的环节。这一页就是那个证据。
 * 其中 ③④ 是核心价值：把「过滤了 8%」变成「这 8% 分别是因为什么」。
 *
 * ⚠️ 与主页的区别：**本页不接 SSE**（决策明确「子页不接 SSE」）。
 *    它不是实时大屏，不需要那套叙事；走普通 REST + 定时刷新即可，
 *    后端零新增。轮询在**这里**没有叙事问题——此前否决轮询，针对的是大屏。
 *
 * ⚠️ 本页同样**不用 Element Plus 组件**。死信明细是个分页表格，
 *    按选型⑤ 的原文，后台类表格本可以用 EP；这里自绘的原因是
 *    **视觉一致性**：整块分析平台是暗色自绘布局，混进一个亮色 EP 表格
 *    会很突兀，且 EP 的全局 reset 样式会渗进同布局下的主页。
 *    表格只有五列、分页只有上一页/下一页，自绘约 60 行，不值得为此破例。
 */

import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import ScreenLayout from '@/layouts/ScreenLayout.vue'
import PanelBox from '@/components/screen/PanelBox.vue'
import EChart from '@/components/screen/EChart.vue'
import {
  getPipelineToday,
  getDirtyRatioTrend,
  getRuleHits,
  listDeadLetters,
  getSourceOptions
} from '@/api/pipeline'
import { dirtyRatioOption, ruleHitsOption } from '@/utils/screenCharts'
import { DIRTY_RULE, DIRTY_SOURCE } from '@/utils/constants'

/* ---------------- ① 今日管道量 ---------------- */

const pipeline = ref(null)

/* ---------------- ② 脏数据占比趋势 ---------------- */

const ratioDays = ref(7)
const ratioRows = ref([])

const ratioChartOption = computed(() =>
  ratioRows.value.length ? dirtyRatioOption(ratioRows.value) : null
)

/* ---------------- ③ 规则命中分布 ---------------- */

const ruleRows = ref([])
const ruleTotal = ref(0)

const ruleChartOption = computed(() =>
  ruleRows.value.length ? ruleHitsOption(ruleRows.value, ruleTotal.value) : null
)

/* ---------------- ④ 死信明细 ---------------- */

const PAGE_SIZE = 5

const deadLetters = ref({ total: 0, pages: 0, current: 1, records: [] })
const sourceFilter = ref('')
const sourceOptions = ref([])
const loading = ref(false)

const sourceBadge = (code) => DIRTY_SOURCE[code]?.label || code
const reasonLabel = (code) => DIRTY_RULE[code]?.label || code

/**
 * 失败时间的显示格式：`YYYY-MM-DD HH:mm:ss`。
 *
 * ⚠️ 后端 `DeadLetter.fail_time` 是 `LocalDateTime`，Jackson 默认吐
 *    ISO-8601（`2026-10-21T04:33:12`），中间那个 `T` 在表格里既占宽
 *    又不合全站约定（`utils/datetime.js`：全站只有一种时间写法）。
 *    这里只做**显示层**的替换，不去改后端序列化——后端那份是标准格式，
 *    别的地方（接口调试、直接看 JSON）依赖它。
 *
 * 顺带截掉毫秒：`toLocalDateTime()` 带出来的 `2026-10-21T04:33:12.123`
 * 多出的三位小数只会让列更拥挤，而这一列展示到秒就够了。
 */
const failTimeText = (v) => (v ? String(v).replace('T', ' ').slice(0, 19) : '')

async function loadDeadLetters(page = 1) {
  loading.value = true
  try {
    deadLetters.value = await listDeadLetters({
      page,
      size: PAGE_SIZE,
      source: sourceFilter.value
    })
  } finally {
    loading.value = false
  }
}

/**
 * 切换来源筛选。
 *
 * ⚠️ 必须**重置到第 1 页**。筛掉一批数据后总数变少，若还停在原来的第 3 页，
 *    会查出空列表——而 `paginate` 会把 current 收敛到合法范围（见 mock/paginate.js），
 *    于是页码自己跳回第 3 页、列表却是空的，看着像「筛完没数据」，
 *    实际上是「筛完数据只有一页」。
 */
function changeSource(value) {
  sourceFilter.value = value
  loadDeadLetters(1)
}

const prevPage = () => loadDeadLetters(Math.max(1, deadLetters.value.current - 1))
const nextPage = () =>
  loadDeadLetters(Math.min(deadLetters.value.pages || 1, deadLetters.value.current + 1))

/* ---------------- 定时刷新 ---------------- */

/**
 * 子页的自动刷新间隔。
 *
 * ⚠️ 刷新的是**管道量与趋势**，不含死信明细的分页——用户正在翻页时
 *    表格自己跳回第一页是很糟的体验。分页数据只在切页/切筛选时取。
 */
const REFRESH_INTERVAL = 30000
let refreshTimer = null

async function loadDashboard() {
  const [today, ratio, rules] = await Promise.all([
    getPipelineToday(),
    getDirtyRatioTrend(ratioDays.value),
    getRuleHits()
  ])
  pipeline.value = today
  ratioRows.value = ratio
  ruleRows.value = rules.rows
  ruleTotal.value = rules.total
}

onMounted(async () => {
  sourceOptions.value = await getSourceOptions()
  await Promise.all([loadDashboard(), loadDeadLetters(1)])
  refreshTimer = setInterval(loadDashboard, REFRESH_INTERVAL)
})

onBeforeUnmount(() => {
  if (refreshTimer) clearInterval(refreshTimer)
  refreshTimer = null
})

/** 切换 7 天 / 30 天。只重取趋势一条，不整体刷新。 */
async function changeRatioDays(days) {
  ratioDays.value = days
  ratioRows.value = await getDirtyRatioTrend(days)
}
</script>

<template>
  <ScreenLayout
    title="电信用户行为分析系统"
    subtitle="系统运行分析 · 管道健康与数据质量"
  >
    <!--
      ⚠️ 原先这里有一个 `#actions` 插槽放「← 返回实时大屏」的跳转链接。
      2026-10-21 拆成三个标签页后**整块删掉**：页与页之间的导航统一由
      `ScreenLayout` 的标签条承担，留着等于同一件事有两个入口，
      而且标签条上已经高亮「系统运行分析」，那个「返回」显得多余。
    -->

    <!-- ① 今日管道量 -->
    <div class="pipe-row">
      <PanelBox title="今日管道量" link="REALTIME" class="pipe-cells">
        <template #extra>
          <span class="pipe-date">{{ pipeline?.date || '--' }}</span>
        </template>

        <div class="pipe-flow">
          <div class="pipe-step">
            <span class="pipe-step__label">采集</span>
            <span class="pipe-step__num">{{ pipeline?.collected ?? '--' }}</span>
            <span class="pipe-step__hint">Kafka + SFTP 收到的原始条数</span>
          </div>

          <span class="pipe-arrow">→</span>

          <div class="pipe-step">
            <span class="pipe-step__label">判脏</span>
            <span class="pipe-step__num is-dirty">{{ pipeline?.dirty ?? '--' }}</span>
            <span class="pipe-step__hint">被 7 条规则拦下，入死信表</span>
          </div>

          <span class="pipe-arrow">→</span>

          <div class="pipe-step">
            <span class="pipe-step__label">入库</span>
            <span class="pipe-step__num is-ok">{{ pipeline?.stored ?? '--' }}</span>
            <span class="pipe-step__hint">写入 dwd_behavior_log</span>
          </div>

          <div class="pipe-rate">
            <span class="pipe-rate__value">{{ pipeline?.passRate ?? '--' }}%</span>
            <span class="pipe-rate__label">入库率</span>
          </div>
        </div>
      </PanelBox>
    </div>

    <!-- ② 脏数据占比趋势 ／ ③ 规则命中分布 -->
    <div class="pipe-row pipe-row--grow">
      <PanelBox title="脏数据占比趋势" link="OFFLINE" class="pipe-cell pipe-cell--wide">
        <template #extra>
          <div class="switcher">
            <button
              v-for="d in [7, 30]"
              :key="d"
              class="switcher__btn"
              :class="{ 'is-active': ratioDays === d }"
              type="button"
              @click="changeRatioDays(d)"
            >
              近 {{ d }} 天
            </button>
          </div>
        </template>
        <EChart :option="ratioChartOption" />
      </PanelBox>

      <PanelBox title="规则命中分布" link="OFFLINE" class="pipe-cell">
        <template #extra>
          <span class="pipe-total">合计 {{ ruleTotal }} 条</span>
        </template>
        <EChart :option="ruleChartOption" />
      </PanelBox>
    </div>

    <!-- ④ 死信明细 -->
    <div class="pipe-row pipe-row--grow">
      <PanelBox title="死信明细" class="pipe-cells">
        <template #extra>
          <div class="switcher">
            <button
              class="switcher__btn"
              :class="{ 'is-active': sourceFilter === '' }"
              type="button"
              @click="changeSource('')"
            >
              全部
            </button>
            <button
              v-for="opt in sourceOptions"
              :key="opt.value"
              class="switcher__btn"
              :class="{ 'is-active': sourceFilter === opt.value }"
              type="button"
              @click="changeSource(opt.value)"
            >
              {{ opt.label }}
            </button>
          </div>
        </template>

        <div class="dl">
          <div class="dl__scroll">
            <table class="dl__table">
              <thead>
                <tr>
                  <th style="width: 66px">ID</th>
                  <th style="width: 84px">来源</th>
                  <th style="width: 132px">失败原因</th>
                  <!-- 180px：字号放大到 14px 后，「2026-10-21 04:33:12」整串要 146px
                       左右，加上左右各 8px 内边距与余量，146 会把秒数截掉 -->
                  <th style="width: 180px">失败时间</th>
                  <th>原始内容</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in deadLetters.records" :key="row.id">
                  <td class="is-dim">{{ row.id }}</td>
                  <td>
                    <span
                      class="dl__tag"
                      :class="row.source === 'REALTIME' ? 'is-realtime' : 'is-offline'"
                    >
                      {{ sourceBadge(row.source) }}
                    </span>
                  </td>
                  <td class="is-reason">{{ reasonLabel(row.fail_reason) }}</td>
                  <td class="is-dim">{{ failTimeText(row.fail_time) }}</td>
                  <!-- raw_content 可能很长（规则 7 判的就是超长），
                       用 title 让鼠标悬停能看到全文，表格本身不撑破 -->
                  <td class="dl__raw" :title="row.raw_content">{{ row.raw_content }}</td>
                </tr>
                <tr v-if="!deadLetters.records.length">
                  <td colspan="5" class="dl__empty">
                    {{ loading ? '加载中…' : '没有符合条件的死信记录' }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="dl__foot">
            <span class="dl__info">
              共 {{ deadLetters.total }} 条 ·
              第 {{ deadLetters.current }} / {{ deadLetters.pages || 1 }} 页
            </span>
            <div class="dl__pager">
              <button
                class="dl__btn"
                type="button"
                :disabled="deadLetters.current <= 1"
                @click="prevPage"
              >
                上一页
              </button>
              <button
                class="dl__btn"
                type="button"
                :disabled="deadLetters.current >= (deadLetters.pages || 1)"
                @click="nextPage"
              >
                下一页
              </button>
            </div>
          </div>
        </div>
      </PanelBox>
    </div>
  </ScreenLayout>
</template>

<style scoped>
.pipe-row {
  display: flex;
  gap: 12px;
}

.pipe-row--grow {
  flex: 1;
  min-height: 0;
}

.pipe-cells {
  flex: 1;
  min-width: 0;
  min-height: 0;
}

.pipe-cell {
  flex: 1;
  min-width: 0;
  min-height: 0;
}

.pipe-cell--wide {
  flex: 1.5;
}

/* ---- ① 管道量 ---- */

.pipe-date,
.pipe-total {
  font-size: 13px;
  color: #7b8db5;
}

.pipe-flow {
  display: flex;
  align-items: center;
  gap: 18px;
  height: 100%;
  padding: 4px 16px 0;
}

.pipe-step {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 132px;
}

.pipe-step__label {
  font-size: 14px;
  color: #8fa6cc;
  letter-spacing: 1px;
}

.pipe-step__num {
  font-family: 'DIN Alternate', 'Bahnschrift', 'Arial Narrow', sans-serif;
  font-size: 32px;
  font-weight: 700;
  line-height: 1.05;
  color: #eaf2ff;
}

.pipe-step__num.is-dirty {
  color: #ff5c8a;
}

.pipe-step__num.is-ok {
  color: #22c1a4;
}

.pipe-step__hint {
  font-size: 12px;
  color: #61729a;
}

.pipe-arrow {
  font-size: 20px;
  color: rgba(120, 160, 220, 0.5);
}

.pipe-rate {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  margin-left: auto;
  padding-right: 12px;
}

.pipe-rate__value {
  font-family: 'DIN Alternate', 'Bahnschrift', 'Arial Narrow', sans-serif;
  font-size: 34px;
  font-weight: 700;
  color: #4fd1e0;
}

.pipe-rate__label {
  font-size: 13px;
  color: #7b8db5;
}

/* ---- ④ 死信表 ---- */

.dl {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.dl__scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.dl__table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
  table-layout: fixed;
}

.dl__table th {
  position: sticky;
  top: 0;
  z-index: 1;
  padding: 5px 8px;
  border-bottom: 1px solid rgba(90, 150, 230, 0.22);
  background: rgba(13, 34, 66, 0.96);
  color: #8fa6cc;
  font-size: 13px;
  font-weight: 500;
  text-align: left;
}

.dl__table td {
  padding: 5px 8px;
  border-bottom: 1px solid rgba(90, 150, 230, 0.08);
  color: #c8d6f0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dl__table tr:hover td {
  background: rgba(61, 139, 255, 0.08);
}

.is-dim {
  color: #7b8db5;
}

.is-reason {
  color: #f5a524;
}

.dl__raw {
  font-family: 'Consolas', 'Courier New', monospace;
  font-size: 13px;
  color: #7f93bb;
}

.dl__tag {
  display: inline-block;
  padding: 0 6px;
  border-radius: 8px;
  font-size: 12px;
  line-height: 18px;
}

.dl__tag.is-realtime {
  color: #4fd1e0;
  border: 1px solid rgba(79, 209, 224, 0.4);
  background: rgba(79, 209, 224, 0.1);
}

.dl__tag.is-offline {
  color: #f5a524;
  border: 1px solid rgba(245, 165, 36, 0.4);
  background: rgba(245, 165, 36, 0.1);
}

.dl__empty {
  padding: 18px !important;
  text-align: center;
  color: #61729a;
}

.dl__foot {
  display: flex;
  align-items: center;
  flex: none;
  padding: 6px 8px 2px;
  border-top: 1px solid rgba(90, 150, 230, 0.14);
}

.dl__info {
  font-size: 13px;
  color: #7b8db5;
}

.dl__pager {
  display: flex;
  gap: 6px;
  margin-left: auto;
}

.dl__btn {
  padding: 3px 12px;
  border: 1px solid rgba(90, 164, 255, 0.4);
  border-radius: 4px;
  background: rgba(61, 139, 255, 0.12);
  color: #9ecbff;
  font-family: inherit;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.dl__btn:hover:not(:disabled) {
  background: rgba(61, 139, 255, 0.28);
  color: #eaf2ff;
}

.dl__btn:disabled {
  border-color: rgba(120, 160, 220, 0.15);
  background: transparent;
  color: #4a5977;
  cursor: not-allowed;
}

/* ---- 切换按钮（与主页同一套外观） ---- */

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
  font-size: 13px;
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
