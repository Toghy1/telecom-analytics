<script setup>
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { getPackageById } from '@/api/package'
import { getRemaining } from '@/api/usage'
import { listRecentBills } from '@/api/bill'
import { BILL_TYPE, CURRENCY } from '@/utils/constants'
import { dateOnly, quantity, yuan } from '@/utils/format'

const auth = useAuthStore()

const account = computed(() => auth.account || {})
const currentPackage = ref(null)
const remaining = ref(null)
const bills = ref([])
const loading = ref(true)

onMounted(async () => {
  try {
    // 三个请求互不依赖，并行发起。
    //
    // 注意 `getPackageById` 与 `getRemaining` 都会去读一次套餐：前者要月费
    // （「当前套餐」卡要显示），后者要三个量纲的额度。假数据阶段多读一次无所谓；
    // 真后端上，「一屏概览」这种页面更适合一个聚合接口（`/account/overview`）
    // 一次给全，省掉三次往返。这一点留到写接口清单时一起定。
    const [pkg, usage, rows] = await Promise.all([
      getPackageById(account.value.package_id),
      getRemaining(),
      listRecentBills(3)
    ])
    currentPackage.value = pkg
    remaining.value = usage
    bills.value = rows
  } catch (e) {
    ElMessage.error(e.message || '数据加载失败')
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="dash">
    <!-- 欢迎条。手机号是**后端脱敏后**下发的（claude.md 选型⑥：
         脱敏在 Entity→VO 手写转换里做），前端不做任何遮盖，
         否则 F12 / Postman 一抓就能看到全量数据，属于假脱敏。 -->
    <section class="dash__hero">
      <h2 class="dash__hero-title">您好，{{ account.login_name }}</h2>
      <p class="dash__hero-sub">
        归属地：{{ account.region || '--' }} · 账户状态：{{
          account.status === 1 ? '正常' : '停用'
        }}
      </p>
    </section>

    <el-skeleton v-if="loading" :rows="5" animated />

    <template v-else>
      <section class="dash__cards">
        <!-- 卡 1：账户余额 -->
        <el-card shadow="hover" class="dash__card">
          <p class="dash__card-label">账户余额</p>
          <p class="dash__amount">
            {{ yuan(account.balance) }}
            <span class="dash__unit">{{ CURRENCY }}</span>
          </p>
          <el-button type="primary" plain size="small" disabled>充值（待开发）</el-button>
        </el-card>

        <!-- 卡 2：当前套餐 -->
        <el-card shadow="hover" class="dash__card">
          <p class="dash__card-label">当前套餐</p>
          <p class="dash__card-value">{{ currentPackage?.name || '未订购' }}</p>
          <p v-if="currentPackage" class="dash__card-note">
            月费 {{ yuan(currentPackage.monthly_fee) }} {{ CURRENCY }}
          </p>
        </el-card>

        <!-- 卡 3：本月余量。
             2026-09-30 之前这里只显示「套餐额度」，因为当时 6 张业务表里没有
             任何地方存「已用量」。现已新增 t_usage（见 claude.md 决策
             「业务数据模型增补 t_usage 用量表」），余量得以真正算出来。
             ⚠️ 减法在 api/usage.js 里做，这里只显示。 -->
        <el-card shadow="hover" class="dash__card">
          <p class="dash__card-label">本月余量</p>
          <ul v-if="remaining" class="dash__remain">
            <li v-for="item in remaining.items" :key="item.key">
              <span class="dash__remain-name">{{ item.label }}</span>
              <span class="dash__remain-value" :class="{ 'is-over': item.remain < 0 }">
                {{ quantity(item.remain) }} {{ item.unit }}
              </span>
              <el-progress
                :percentage="item.percent"
                :status="item.remain < 0 ? 'exception' : undefined"
                :show-text="false"
                :stroke-width="6"
              />
            </li>
          </ul>
          <p v-else class="dash__card-note">--</p>
        </el-card>
      </section>

      <!-- 最近账单 -->
      <section class="dash__block">
        <h3 class="dash__block-title">最近账单</h3>
        <el-table :data="bills" stripe>
          <el-table-column prop="bill_month" label="账单月份" width="110">
            <template #default="{ row }">{{ row.bill_month || '--' }}</template>
          </el-table-column>
          <el-table-column prop="remark" label="说明" min-width="200" />
          <el-table-column label="发生时间" width="130">
            <template #default="{ row }">{{ dateOnly(row.occur_time) }}</template>
          </el-table-column>
          <el-table-column label="金额" width="120" align="right">
            <!-- 金额一律存正数、方向靠 type 区分，所以这里要用 type 决定正负号，
                 不能指望 amount 自带符号。 -->
            <template #default="{ row }">
              {{ BILL_TYPE[row.type]?.sign }}{{ yuan(row.amount) }} {{ CURRENCY }}
            </template>
          </el-table-column>
        </el-table>
      </section>
    </template>
  </div>
</template>

<style scoped>
.dash__hero {
  padding: 20px 24px;
  border-radius: var(--tp-radius);
  background: linear-gradient(120deg, #1f6feb 0%, #4b93f7 100%);
  color: #fff;
}

.dash__hero-title {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 600;
}

.dash__hero-sub {
  margin: 0;
  font-size: 13px;
  opacity: 0.9;
}

.dash__cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
  margin-top: 20px;
}

.dash__card-label {
  margin: 0 0 10px;
  color: var(--tp-text-sub);
  font-size: 13px;
}

.dash__amount {
  margin: 0 0 14px;
  color: var(--tp-primary);
  font-size: 28px;
  font-weight: 700;
}

.dash__unit {
  margin-left: 4px;
  color: var(--tp-text-sub);
  font-size: 13px;
  font-weight: 400;
}

.dash__card-value {
  margin: 0 0 6px;
  font-size: 18px;
  font-weight: 600;
}

.dash__card-note {
  margin: 0;
  color: var(--tp-text-sub);
  font-size: 12px;
}

.dash__remain {
  margin: 0;
  padding: 0;
  list-style: none;
}

.dash__remain li {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: baseline;
  gap: 4px 8px;
  margin-bottom: 10px;
}

.dash__remain li:last-child {
  margin-bottom: 0;
}

.dash__remain-name {
  color: var(--tp-text-sub);
  font-size: 12px;
}

.dash__remain-value {
  font-size: 13px;
  font-weight: 600;
}

.dash__remain-value.is-over {
  color: #f56c6c;
}

/* 进度条要占满整行，所以在 grid 里跨两列 */
.dash__remain :deep(.el-progress) {
  grid-column: 1 / -1;
}

.dash__block {
  margin-top: 28px;
}

.dash__block-title {
  margin: 0 0 14px;
  padding-left: 10px;
  border-left: 3px solid var(--tp-primary);
  font-size: 16px;
  font-weight: 600;
}
</style>
