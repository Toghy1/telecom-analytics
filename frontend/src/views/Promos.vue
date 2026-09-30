<script setup>
/**
 * 促销活动浏览（游客可见）。
 *
 * 与首页的「优惠活动」是同一批数据的两种用法（同套餐资费浏览）：
 *   首页 —— 前 3 条 + 「更多」入口；本页 —— 全量。
 *
 * ⚠️ 本页比首页多显示一项「适用套餐」。
 *    这不是装饰：`t_promo.package_id` 是这张表唯一还没在首页露面的信息，
 *    浏览页若连它都不显示，这一页就是首页卡片的复制品——那还不如不建。
 *
 * 埋点 `view_promo` 由路由守卫自动上报（选型⑦）。
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listPromos } from '@/api/promo'
import { useAuthStore } from '@/stores/auth'
import PromoCard from '@/components/PromoCard.vue'

const router = useRouter()
const auth = useAuthStore()

const promos = ref([])
const loading = ref(true)

onMounted(async () => {
  try {
    promos.value = await listPromos()
  } catch (e) {
    ElMessage.error(e.message || '数据加载失败')
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="promos">
    <header class="promos__head">
      <h2 class="promos__title">优惠活动</h2>
      <p v-if="!loading" class="promos__sub">共 {{ promos.length }} 个进行中的活动</p>
    </header>

    <el-skeleton v-if="loading" :rows="6" animated />

    <template v-else>
      <el-empty v-if="!promos.length" description="暂无进行中的活动" />
      <div v-else class="card-grid card-grid--wide">
        <!-- package_name 由 api 层把 package_id 解析好后带出来（真实后端是 SQL JOIN） -->
        <PromoCard
          v-for="item in promos"
          :key="item.id"
          :promo="item"
          :package-name="item.package_name"
        />
      </div>

      <p class="promos__foot">
        活动详情以营业厅实际执行为准，登录后可查询您当前订购的套餐。
        <el-button
          v-if="!auth.isLoggedIn"
          link
          type="primary"
          @click="router.push({ name: 'login' })"
        >
          前往登录
        </el-button>
      </p>
    </template>
  </div>
</template>

<style scoped>
.promos__head {
  margin-bottom: 18px;
}

.promos__title {
  margin: 0 0 6px;
  padding-left: 10px;
  border-left: 3px solid var(--tp-primary);
  font-size: 18px;
  font-weight: 600;
}

.promos__sub {
  margin: 0 0 0 13px;
  color: var(--tp-text-sub);
  font-size: 13px;
}

.promos__foot {
  margin: 22px 0 0;
  color: var(--tp-text-sub);
  font-size: 13px;
}
</style>
