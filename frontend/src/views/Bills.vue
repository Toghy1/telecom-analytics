<script setup>
/**
 * 账单查询（《开发文档》§6.4 #3）：按月列表 + 该月明细。
 *
 * 两栏的布局不是为好看——《开发文档》把这一页写成「按月列表**+**明细」，
 * 就是两样东西都要看得见。若做成「上方一个月份下拉框 + 下方明细表」，
 * 按月列表就退化成一个控件，各月消费多少得逐月点开才知道。
 * 左栏把 5 个账期连同合计一次摆出来，右栏只管当前选中的那一个月。
 *
 * 埋点 `query_bill` 由路由守卫自动上报，页面里不写。
 */
import { computed, onMounted, ref } from 'vue'
import { listBillMonths, listBills } from '@/api/bill'
import { CURRENCY } from '@/utils/constants'
import { yuan } from '@/utils/format'

const months = ref([])
const selectedMonth = ref('')

const rows = ref([])
const total = ref(0)
const currentPage = ref(1)
const loading = ref(true)
const monthLoading = ref(true)

/** 每页 5 条。9 月有 6 笔明细，正好第二页有内容，分页真的翻得动。 */
const PAGE_SIZE = 5

/** 左栏选中那个月的合计，直接取自按月列表，不必再请求一次。 */
const selectedSummary = computed(() => months.value.find((m) => m.bill_month === selectedMonth.value))

async function loadDetail() {
  loading.value = true
  try {
    const data = await listBills({ month: selectedMonth.value, page: currentPage.value, size: PAGE_SIZE })
    rows.value = data.records
    total.value = data.total
    currentPage.value = data.current
  } catch (e) {
    ElMessage.error(e.message || '账单明细加载失败')
  } finally {
    loading.value = false
  }
}

/**
 * 切换账期。
 *
 * ⚠️ 必须把页码复位到第 1 页。否则会出现这种情况：在 9 月翻到第 2 页，
 * 再点 8 月——8 月只有 2 笔、只有 1 页，于是请求 `page=2` 过去。
 * 后端把页码收敛到 1 返回第 1 页数据，页面看着正常，但「我点的是 8 月啊」
 * 和「怎么还是显示第 2 页高亮」这类错觉都从这里来。切换上下文就重置分页，
 * 是一条一行代码的成本换来的确定性。
 */
function selectMonth(month) {
  if (month === selectedMonth.value) return
  selectedMonth.value = month
  currentPage.value = 1
  loadDetail()
}

onMounted(async () => {
  try {
    months.value = await listBillMonths()
    // 默认选最近一个账期：查账单的人十有八九是来看这个月的
    selectedMonth.value = months.value[0]?.bill_month || ''
    if (selectedMonth.value) await loadDetail()
  } catch (e) {
    ElMessage.error(e.message || '账单加载失败')
  } finally {
    monthLoading.value = false
    loading.value = false
  }
})
</script>

<template>
  <div class="bills">
    <header class="bills__head">
      <h2 class="bills__title">账单查询</h2>
      <p class="bills__sub">按账期查看每月消费合计与费用明细</p>
    </header>

    <div class="bills__body">
      <!-- 左栏：按月列表。即《开发文档》§5.2 ④ 的
           `WHERE type='BILL' GROUP BY bill_month`。 -->
      <aside class="bills__side">
        <h3 class="bills__side-title">按月列表</h3>
        <el-skeleton v-if="monthLoading" :rows="4" animated />
        <ul v-else class="bills__months">
          <li
            v-for="m in months"
            :key="m.bill_month"
            class="bills__month"
            :class="{ 'is-active': m.bill_month === selectedMonth }"
            @click="selectMonth(m.bill_month)"
          >
            <span class="bills__month-label">{{ m.bill_month }}</span>
            <span class="bills__month-amount">{{ yuan(m.amount) }} {{ CURRENCY }}</span>
            <span class="bills__month-count">{{ m.count }} 笔</span>
          </li>
        </ul>
      </aside>

      <!-- 右栏：当前账期的明细。 -->
      <section class="bills__main">
        <h3 class="bills__main-title">
          {{ selectedMonth || '--' }} 费用明细
          <span v-if="selectedSummary" class="bills__main-extra">
            合计 {{ yuan(selectedSummary.amount) }} {{ CURRENCY }} · 共 {{ total }} 笔
          </span>
        </h3>

        <el-table v-loading="loading" :data="rows" stripe empty-text="该账期暂无消费记录">
          <el-table-column prop="remark" label="费用说明" min-width="200" />
          <el-table-column label="发生时间" width="170">
            <template #default="{ row }">{{ row.occur_time }}</template>
          </el-table-column>
          <el-table-column label="金额" width="110" align="right">
            <!-- 一律显示为消费（负号）。这个查询本身就只取 type='BILL'，
                 缴费记录在话费查询页，两边口径不混。 -->
            <template #default="{ row }">-{{ yuan(row.amount) }} {{ CURRENCY }}</template>
          </el-table-column>
        </el-table>

        <el-pagination
          v-model:current-page="currentPage"
          class="bills__pager"
          background
          layout="total, prev, pager, next"
          :page-size="PAGE_SIZE"
          :total="total"
          @current-change="loadDetail"
        />
      </section>
    </div>
  </div>
</template>

<style scoped>
.bills__head {
  margin-bottom: 18px;
}

.bills__title {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 600;
}

.bills__sub {
  margin: 0;
  color: var(--tp-text-sub);
  font-size: 13px;
}

.bills__body {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.bills__side {
  flex: 0 0 210px;
  padding: 16px 12px;
  border-radius: var(--tp-radius);
  background: #fff;
}

.bills__side-title {
  margin: 0 0 12px;
  padding-left: 8px;
  border-left: 3px solid var(--tp-primary);
  font-size: 14px;
  font-weight: 600;
}

.bills__months {
  margin: 0;
  padding: 0;
  list-style: none;
}

.bills__month {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 2px 8px;
  padding: 9px 10px;
  border-radius: 6px;
  cursor: pointer;
  transition: background-color 0.2s;
}

.bills__month:hover {
  background: #f0f5ff;
}

.bills__month.is-active {
  background: #ecf3fe;
}

.bills__month-label {
  font-size: 14px;
  font-weight: 600;
}

.bills__month.is-active .bills__month-label {
  color: var(--tp-primary);
}

.bills__month-amount {
  font-size: 13px;
  text-align: right;
}

.bills__month-count {
  grid-column: 1 / -1;
  color: var(--tp-text-sub);
  font-size: 12px;
}

.bills__main {
  flex: 1;
  min-width: 0;
  padding: 16px 16px 8px;
  border-radius: var(--tp-radius);
  background: #fff;
}

.bills__main-title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin: 0 0 14px;
  padding-left: 10px;
  border-left: 3px solid var(--tp-primary);
  font-size: 16px;
  font-weight: 600;
}

.bills__main-extra {
  color: var(--tp-text-sub);
  font-size: 12px;
  font-weight: 400;
}

.bills__pager {
  display: flex;
  justify-content: flex-end;
  padding: 14px 0;
}
</style>
