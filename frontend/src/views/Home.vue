<script setup>
/**
 * 营业厅首页（游客可见）。
 *
 * 本页只做**节选**：活动 3 条、套餐 3 款，各自给一个「更多」入口，
 * 点进去是 `Promos.vue` / `Packages.vue` 的全量页。
 *
 * 为什么不把全部铺在首页：卡片已经抽成组件（`components/PromoCard.vue`、
 * `components/PackageCard.vue`），首页铺全量与铺节选是同一份模板、同一份数据，
 * 铺全量只会让首页变长，多出来的部分和浏览页一模一样——用户滑到第三屏
 * 还是同一批卡片，反而更像「这页没加载完」。节选 + 入口把「首页」和
 * 「浏览页」的职责分开：前者是概览，后者是清单。
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listPackages } from '@/api/package'
import { listPromos } from '@/api/promo'
import { useAuthStore } from '@/stores/auth'
import PackageCard from '@/components/PackageCard.vue'
import PromoCard from '@/components/PromoCard.vue'

/** 首页每类展示几条。留成常量，改这一个数就行，不用去模板里数 v-for。 */
const HOME_LIMIT = 3

const router = useRouter()
const auth = useAuthStore()

const packages = ref([])
const promos = ref([])
const loading = ref(true)

onMounted(async () => {
  try {
    // 两个请求互不依赖，并行发起，省一半等待。
    // limit 交给 api 层，不在页面里 slice()——接后端时首页一行都不用改。
    const [packageList, promoList] = await Promise.all([
      listPackages({ limit: HOME_LIMIT }),
      listPromos({ limit: HOME_LIMIT })
    ])
    packages.value = packageList
    promos.value = promoList
  } catch (e) {
    ElMessage.error(e.message || '数据加载失败')
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="home">
    <!-- 顶部欢迎条：游客与登录用户看到的是两种话术 -->
    <section class="hero">
      <div class="hero__text">
        <h2 class="hero__title">欢迎使用电信自助营业厅</h2>
        <p v-if="auth.isLoggedIn" class="hero__sub">
          您已登录（{{ auth.displayName }}），可办理套餐、查询话费与账单。
        </p>
        <p v-else class="hero__sub">
          登录后可查询话费、账单、余量，并在线办理套餐与报障。
        </p>
      </div>
      <el-button
        v-if="!auth.isLoggedIn"
        type="primary"
        size="large"
        @click="router.push({ name: 'login' })"
      >
        立即登录
      </el-button>
    </section>

    <el-skeleton v-if="loading" :rows="6" animated />

    <template v-else>
      <!-- 促销活动（节选） -->
      <section class="block">
        <div class="block__head">
          <h3 class="block__title">优惠活动</h3>
          <router-link :to="{ name: 'promos' }" class="block__more">更多活动 →</router-link>
        </div>
        <el-empty v-if="!promos.length" description="暂无进行中的活动" />
        <!-- 首页不传 package-name：卡片保持三行，一屏扫得完。
             活动与套餐的关联放到浏览页再展开。 -->
        <div v-else class="card-grid card-grid--wide">
          <PromoCard v-for="item in promos" :key="item.id" :promo="item" />
        </div>
      </section>

      <!-- 套餐资费（节选） -->
      <section class="block">
        <div class="block__head">
          <h3 class="block__title">套餐资费</h3>
          <router-link :to="{ name: 'packages' }" class="block__more">更多套餐 →</router-link>
        </div>
        <el-empty v-if="!packages.length" description="暂无可办理套餐" />
        <div v-else class="card-grid">
          <PackageCard v-for="item in packages" :key="item.id" :pkg="item" />
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 28px 32px;
  border-radius: var(--tp-radius);
  background: linear-gradient(120deg, #1f6feb 0%, #4b93f7 100%);
  color: #fff;
}

.hero__title {
  margin: 0 0 8px;
  font-size: 22px;
  font-weight: 600;
}

.hero__sub {
  margin: 0;
  font-size: 14px;
  opacity: 0.9;
}

.block {
  margin-top: 28px;
}

/* 标题与「更多」入口同一行：入口挂在标题右侧，
   读的人先看到「这是什么」，再决定要不要看全部。 */
.block__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 14px;
}

.block__title {
  margin: 0;
  padding-left: 10px;
  border-left: 3px solid var(--tp-primary);
  font-size: 16px;
  font-weight: 600;
}

.block__more {
  color: var(--tp-text-sub);
  font-size: 13px;
}

.block__more:hover {
  color: var(--tp-primary);
}
</style>
