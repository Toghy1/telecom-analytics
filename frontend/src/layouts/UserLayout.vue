<script setup>
// 用户端布局：顶栏 + 左侧导航 + 内容 + 页脚。
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'

const route = useRoute()

/**
 * 用户端七个页面（《开发文档》§6.4）。
 *
 * `ready: false` 的还没建页面。这一项**不是装饰**：如果侧边栏把没建的页面
 * 也做成可点的链接，点下去会命中路由表末尾的通配规则弹回首页——
 * 用户看到的是「点了没反应」，而排查的人要顺着路由表翻到最后才能看明白。
 * 灰掉 + 标「待开发」，是把「还没做」直接说清楚。
 */
const navItems = [
  { name: 'dashboard', path: '/user/dashboard', label: '账户总览', ready: true },
  { name: 'balance', path: '/user/balance', label: '话费查询', ready: true },
  { name: 'bills', path: '/user/bills', label: '账单查询', ready: true },
  { name: 'usage', path: '/user/usage', label: '余量查询', ready: true },
  { name: 'order', path: '/user/order', label: '套餐办理', ready: true },
  { name: 'ticket', path: '/user/ticket', label: '在线报障', ready: true },
  { name: 'profile', path: '/user/profile', label: '个人信息', ready: true }
]

const activePath = computed(() => route.path)
</script>

<template>
  <div class="user">
    <AppHeader />

    <div class="user__body">
      <aside class="user__side">
        <el-menu :default-active="activePath" router class="user__menu">
          <el-menu-item
            v-for="item in navItems"
            :key="item.path"
            :index="item.path"
            :disabled="!item.ready"
          >
            <span class="user__menu-label">{{ item.label }}</span>
            <el-tag v-if="!item.ready" size="small" type="info" effect="plain">待开发</el-tag>
          </el-menu-item>
        </el-menu>
      </aside>

      <main class="user__main">
        <router-view />
      </main>
    </div>

    <AppFooter />
  </div>
</template>

<style scoped>
.user {
  display: flex;
  flex-direction: column;
  min-height: 100%;
}

.user__body {
  display: flex;
  flex: 1;
  gap: 20px;
  width: 100%;
  max-width: 1160px;
  margin: 0 auto;
  padding: 20px;
}

.user__side {
  flex: 0 0 180px;
}

.user__menu {
  border-right: none;
  border-radius: 6px;
  background: #fff;
}

.user__menu-label {
  flex: 1;
}

.user__main {
  flex: 1;
  min-width: 0;
}
</style>
