<script setup>
// 游客端布局：顶栏 + 内容 + 页脚。不放侧边导航——
// 游客端只有首页 / 套餐资费 / 优惠活动三个页面，且都在顶栏里，
// 再挂一层侧边导航反而占掉内容宽度（用户端页面多，才需要侧边栏）。
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
</script>

<template>
  <div class="portal">
    <AppHeader />

    <main class="portal__main">
      <router-view />
    </main>

    <AppFooter />
  </div>
</template>

<style scoped>
.portal {
  display: flex;
  flex-direction: column;
  min-height: 100%;
}

.portal__main {
  flex: 1;
  width: 100%;
  max-width: 1160px;
  margin: 0 auto;
  padding: 24px 20px 40px;
}
</style>
