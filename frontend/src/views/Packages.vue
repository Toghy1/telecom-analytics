<script setup>
/**
 * 套餐资费浏览（游客可见）。
 *
 * 与首页的「套餐资费」是同一批数据的两种用法：
 *   首页 —— 前 3 个 + 「更多」入口，一屏扫得完，且留了往下走的理由；
 *   本页 —— 全量，就是首页那个「更多」的落点。
 * 卡片本身是同一个组件（`components/PackageCard.vue`），两处不会长得不一样。
 *
 * 埋点 `view_package_list` 由路由守卫自动上报（选型⑦），本页不写一行上报代码。
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listPackages } from '@/api/package'
import { useAuthStore } from '@/stores/auth'
import PackageCard from '@/components/PackageCard.vue'

const router = useRouter()
const auth = useAuthStore()

const packages = ref([])
const loading = ref(true)

onMounted(async () => {
  try {
    // 不传 limit —— 浏览页要的就是全量
    packages.value = await listPackages()
  } catch (e) {
    ElMessage.error(e.message || '数据加载失败')
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="pkgs">
    <header class="pkgs__head">
      <h2 class="pkgs__title">套餐资费</h2>
      <p v-if="!loading" class="pkgs__sub">共 {{ packages.length }} 款在售套餐</p>
    </header>

    <el-skeleton v-if="loading" :rows="6" animated />

    <template v-else>
      <el-empty v-if="!packages.length" description="暂无可办理套餐" />
      <div v-else class="card-grid">
        <PackageCard v-for="item in packages" :key="item.id" :pkg="item" />
      </div>

      <!-- 游客走到这一步已经看完套餐了，这里给一个自然的下一步。
           没有登录的人不显示「办理」按钮——那条路要先进用户端，
           在这里放一个点了会被守卫弹回来的按钮，比不放更差。 -->
      <p class="pkgs__foot">
        以上为在售套餐，登录后可自助办理套餐变更。
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
.pkgs__head {
  margin-bottom: 18px;
}

.pkgs__title {
  margin: 0 0 6px;
  padding-left: 10px;
  border-left: 3px solid var(--tp-primary);
  font-size: 18px;
  font-weight: 600;
}

.pkgs__sub {
  margin: 0 0 0 13px;
  color: var(--tp-text-sub);
  font-size: 13px;
}

.pkgs__foot {
  margin: 22px 0 0;
  color: var(--tp-text-sub);
  font-size: 13px;
}
</style>
