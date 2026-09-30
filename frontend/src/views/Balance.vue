<script setup>
/**
 * 话费查询（《开发文档》§6.4 #2）：余额 + 缴费记录。
 *
 * 埋点 `query_balance` 不在这里写——它是**页面浏览类**事件，由路由守卫统一上报
 * （选型⑦，见 router/index.js 的 afterEach）。页面里一行上报代码都没有，
 * 这是刻意的：分散上报迟早会有页面漏掉，而漏掉不会报错，只是漏斗少一段。
 */
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { listPayments } from '@/api/bill'
import { BILL_TYPE, CURRENCY } from '@/utils/constants'
import { yuan } from '@/utils/format'

const auth = useAuthStore()
const account = computed(() => auth.account || {})

const rows = ref([])
const total = ref(0)
const currentPage = ref(1)
const loading = ref(true)

/** 每页 5 条。假数据里当前用户有 7 笔缴费，正好跨两页——分页控件不是摆设。 */
const PAGE_SIZE = 5

async function load() {
  loading.value = true
  try {
    const data = await listPayments({ page: currentPage.value, size: PAGE_SIZE })
    rows.value = data.records
    total.value = data.total
    // 回写 current：后端会把越界的页码收敛到有效范围（如删了记录后停在第 3 页），
    // 以它返回的为准，页面才能和实际数据对上。
    currentPage.value = data.current
  } catch (e) {
    ElMessage.error(e.message || '缴费记录加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="bal">
    <header class="bal__head">
      <h2 class="bal__title">话费查询</h2>
      <p class="bal__sub">查看账户可用余额与历史缴费记录</p>
    </header>

    <!-- 余额卡。数据取自登录时下发的账号信息，与账户总览同一个来源。 -->
    <el-card shadow="never" class="bal__card">
      <p class="bal__label">账户可用余额</p>
      <p class="bal__amount">
        {{ yuan(account.balance) }}
        <span class="bal__unit">{{ CURRENCY }}</span>
      </p>
      <p class="bal__note">余额可用于抵扣每月套餐月费及套餐外消费。</p>
    </el-card>

    <section class="bal__block">
      <h3 class="bal__block-title">
        缴费记录
        <span class="bal__block-extra">共 {{ total }} 笔</span>
      </h3>

      <el-table v-loading="loading" :data="rows" stripe empty-text="暂无缴费记录">
        <el-table-column label="缴费时间" width="180">
          <template #default="{ row }">{{ row.occur_time }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="缴费方式" min-width="160" />
        <el-table-column label="缴费金额" width="130" align="right">
          <!-- 金额一律存正数、方向靠 type 区分，所以这个「+」是从 BILL_TYPE 取的，
               不是 amount 自带的。把正负号当成数据的一部分是选型时明确否掉的做法。 -->
          <template #default="{ row }">
            <span class="bal__income">
              {{ BILL_TYPE[row.type]?.sign }}{{ yuan(row.amount) }} {{ CURRENCY }}
            </span>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="currentPage"
        class="bal__pager"
        background
        layout="total, prev, pager, next"
        :page-size="PAGE_SIZE"
        :total="total"
        @current-change="load"
      />
    </section>
  </div>
</template>

<style scoped>
.bal__head {
  margin-bottom: 18px;
}

.bal__title {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 600;
}

.bal__sub {
  margin: 0;
  color: var(--tp-text-sub);
  font-size: 13px;
}

.bal__card {
  border: none;
  border-radius: var(--tp-radius);
}

.bal__label {
  margin: 0 0 10px;
  color: var(--tp-text-sub);
  font-size: 13px;
}

.bal__amount {
  margin: 0 0 10px;
  color: var(--tp-primary);
  font-size: 32px;
  font-weight: 700;
}

.bal__unit {
  margin-left: 4px;
  color: var(--tp-text-sub);
  font-size: 14px;
  font-weight: 400;
}

.bal__note {
  margin: 0;
  color: var(--tp-text-sub);
  font-size: 12px;
}

.bal__block {
  margin-top: 28px;
  padding: 18px 18px 8px;
  border-radius: var(--tp-radius);
  background: #fff;
}

.bal__block-title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin: 0 0 14px;
  padding-left: 10px;
  border-left: 3px solid var(--tp-primary);
  font-size: 16px;
  font-weight: 600;
}

.bal__block-extra {
  color: var(--tp-text-sub);
  font-size: 12px;
  font-weight: 400;
}

.bal__income {
  color: #67c23a;
  font-weight: 600;
}

.bal__pager {
  display: flex;
  justify-content: flex-end;
  padding: 14px 0;
}
</style>
