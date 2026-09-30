<script setup>
/**
 * 余量查询（《开发文档》§6.4 #4）：流量 / 通话 / 短信剩余。
 *
 * ⚠️ 页面里**没有减法**。「剩余 = 套餐额度 − 本月已用」这笔账在
 *    `src/api/usage.js` 的 `getRemaining()` 里算完才交过来（那里写了为什么）。
 *    页面拿到的是 `quota` / `used` / `remain` 三个数，只负责显示。
 *
 * 埋点 `query_usage` 由路由守卫自动上报，页面里不写。
 */
import { onMounted, ref } from 'vue'
import { getRemaining } from '@/api/usage'
import { quantity } from '@/utils/format'

const remaining = ref(null)
const loading = ref(true)

onMounted(async () => {
  try {
    remaining.value = await getRemaining()
  } catch (e) {
    ElMessage.error(e.message || '余量加载失败')
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="usage">
    <header class="usage__head">
      <h2 class="usage__title">余量查询</h2>
      <p v-if="remaining" class="usage__sub">
        账期 {{ remaining.usage_month }} · 当前套餐：{{ remaining.package_name }}
      </p>
      <p v-else class="usage__sub">查询本账期套餐余量</p>
    </header>

    <el-skeleton v-if="loading" :rows="6" animated />

    <template v-else-if="remaining">
      <!-- 「查不到本月用量行」是数据问题而不是「一点没用」，见 api/usage.js 的说明。
           这里显式说出来，免得把它当成「额度全剩」看。 -->
      <el-alert
        v-if="!remaining.has_usage"
        type="warning"
        :closable="false"
        show-icon
        title="未查到本账期的用量记录"
        description="以下按「已用 0」展示，实际用量可能尚未统计入库。"
      />

      <section class="usage__grid">
        <el-card v-for="item in remaining.items" :key="item.key" shadow="never" class="usage__card">
          <p class="usage__label">{{ item.label }}</p>

          <p class="usage__remain" :class="{ 'is-over': item.remain < 0 }">
            {{ quantity(item.remain) }}
            <span class="usage__unit">{{ item.unit }}</span>
          </p>
          <p class="usage__caption">
            {{ item.remain < 0 ? '已超出套餐' : '剩余' }}
          </p>

          <!-- 超套时进度条封顶 100%（percent 由 api 侧封过顶）并把条染红。
               注意**数字不跟着封顶**：上面那个负数照实显示。
               进度条画满是一种视觉表达，数字是事实，两者不能互相迁就。 -->
          <el-progress
            class="usage__bar"
            :percentage="item.percent"
            :status="item.remain < 0 ? 'exception' : undefined"
            :stroke-width="10"
          />

          <p class="usage__detail">
            已用 <b>{{ quantity(item.used) }}</b> ／ 套餐 {{ quantity(item.quota) }} {{ item.unit }}
          </p>
        </el-card>
      </section>

      <p class="usage__footnote">
        余量 ＝ 套餐额度 − 本月已用量，数据取自用量表（一人一月一行）。超套餐部分另行计费，
        可在「账单查询」中查看。
      </p>
    </template>
  </div>
</template>

<style scoped>
.usage__head {
  margin-bottom: 18px;
}

.usage__title {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 600;
}

.usage__sub {
  margin: 0;
  color: var(--tp-text-sub);
  font-size: 13px;
}

.usage__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
  margin-top: 16px;
}

.usage__card {
  border: none;
  border-radius: var(--tp-radius);
}

.usage__label {
  margin: 0 0 12px;
  color: var(--tp-text-sub);
  font-size: 13px;
}

.usage__remain {
  margin: 0;
  color: var(--tp-primary);
  font-size: 30px;
  font-weight: 700;
  line-height: 1.1;
}

.usage__remain.is-over {
  color: #f56c6c;
}

.usage__unit {
  margin-left: 4px;
  color: var(--tp-text-sub);
  font-size: 13px;
  font-weight: 400;
}

.usage__caption {
  margin: 2px 0 16px;
  color: var(--tp-text-sub);
  font-size: 12px;
}

.usage__bar {
  margin-bottom: 12px;
}

.usage__detail {
  margin: 0;
  color: #606266;
  font-size: 12px;
}

.usage__footnote {
  margin: 22px 0 0;
  color: var(--tp-text-sub);
  font-size: 12px;
  line-height: 1.8;
}
</style>
