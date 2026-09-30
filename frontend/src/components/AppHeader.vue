<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { reportAction } from '@/utils/tracker'
import { isRoleAllowed } from '@/router/access'

const router = useRouter()
const auth = useAuthStore()

/**
 * 「我的账户」这个入口该不该显示。
 *
 * ⚠️ 不写 `auth.account?.role === 'USER'`，而是去问 `isRoleAllowed()`——
 *    谁能进 `/user` 只在路由表的 `meta.roles` 里写一份，这里读那一份。
 *    否则路由一改，这里不报错，只会留一个点不通的入口。
 *
 * ⚠️ 用 `router.resolve()` 拿到的是**同一条路由**在守卫里看到的那份 meta，
 *    所以两者不可能不一致。
 */
const canEnterUserArea = computed(() =>
  isRoleAllowed(router.resolve({ name: 'dashboard' }).meta, auth.account?.role)
)

async function handleLogout() {
  // ⚠️ 埋点必须在清登录态**之前**上报。
  // auth.logout() 会把 localStorage 里的账号清掉，而 reportAction 是从
  // localStorage 取 user_id 的——顺序反了的话，logout 事件的 user_id 会是 null，
  // 在数据侧看起来与游客事件毫无区别，退出行为就统计不出来了。
  reportAction('logout') // 选型⑦：交互动作类事件手动上报

  await auth.logout()
  ElMessage.success('已退出登录')

  // ⚠️ 已经在首页时不要再 push 一次。
  // 重复导航会让路由守卫再跑一遍，凭空多上报一条 view_home——
  // 用户在首页点「退出」，并没有产生新的页面浏览，但页面热度/PV 会被抬高。
  if (router.currentRoute.value.name !== 'home') {
    router.push({ name: 'home' })
  }
}
</script>

<template>
  <header class="hdr">
    <div class="hdr__inner">
      <div class="hdr__brand">
        <span class="hdr__logo">电信</span>
        <span class="hdr__title">自助营业厅</span>
      </div>

      <nav class="hdr__nav">
        <router-link :to="{ name: 'home' }" class="hdr__nav-item">首页</router-link>
        <!-- 套餐与活动是游客可见的公开页，无论登没登录都显示。
             首页只放节选，用户从任意页面想直接翻全量时，走这里比回首页再点更近。 -->
        <router-link :to="{ name: 'packages' }" class="hdr__nav-item">套餐资费</router-link>
        <router-link :to="{ name: 'promos' }" class="hdr__nav-item">优惠活动</router-link>
        <!-- 未登录时不显示入口，否则点进去会被守卫弹回登录页，白跑一趟。
             ⚠️ 判断用 `canEnterUserArea` 而不是 `auth.isLoggedIn`：`/user` 的
             `roles` 只列了 `USER`，ADMIN 登录后同样「已登录」却进不去——
             只判登录态的话，管理员会看到一个点了就被弹回登录页的「我的账户」，
             而这种「点了弹走」的入口比没有入口更糟，用户会以为系统坏了。 -->
        <router-link v-if="canEnterUserArea" :to="{ name: 'dashboard' }" class="hdr__nav-item">
          我的账户
        </router-link>
      </nav>

      <div class="hdr__user">
        <template v-if="auth.isLoggedIn">
          <span class="hdr__welcome">您好，{{ auth.displayName }}</span>
          <el-button link type="primary" @click="handleLogout">退出登录</el-button>
        </template>
        <el-button v-else type="primary" @click="router.push({ name: 'login' })">登录</el-button>
      </div>
    </div>
  </header>
</template>

<style scoped>
.hdr {
  position: sticky;
  top: 0;
  z-index: 10;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
}

.hdr__inner {
  display: flex;
  align-items: center;
  gap: 32px;
  max-width: 1160px;
  height: 60px;
  margin: 0 auto;
  padding: 0 20px;
}

.hdr__brand {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.hdr__logo {
  padding: 2px 8px;
  border-radius: 4px;
  background: var(--tp-primary);
  color: #fff;
  font-size: 15px;
  font-weight: 700;
}

.hdr__title {
  font-size: 17px;
  font-weight: 600;
  letter-spacing: 1px;
}

.hdr__nav {
  display: flex;
  flex: 1;
  gap: 24px;
}

.hdr__nav-item {
  padding: 4px 0;
  border-bottom: 2px solid transparent;
  color: var(--tp-text-sub);
  font-size: 15px;
}

.hdr__nav-item.router-link-exact-active {
  border-bottom-color: var(--tp-primary);
  color: var(--tp-primary);
  font-weight: 600;
}

.hdr__user {
  display: flex;
  align-items: center;
  gap: 12px;
}

.hdr__welcome {
  color: var(--tp-text-sub);
  font-size: 13px;
}
</style>
