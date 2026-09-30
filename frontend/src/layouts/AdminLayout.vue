<script setup>
// 管理后台布局：顶栏 + 左侧导航 + 内容 + 页脚。第三个路由布局
// （前两个是 PortalLayout / UserLayout）。
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import AppFooter from '@/components/AppFooter.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

/**
 * 管理后台三个页面（《开发文档》§6.6）。本模块是**整个前端的最后一页**，
 * 所以三项当前全部就绪。
 *
 * ⚠️ `ready` 这套机制**不是装饰，故意保留着**（与 `UserLayout` 逐字同形）。
 *    路由表末尾有一条 `{ path: '/:pathMatch(.*)*', redirect: '/' }` 兜底，
 *    侧边栏若把没建的页面做成可点的链接，点下去会命中那条通配规则弹回首页——
 *    用户看到的是「点了没反应」，而排查的人要顺着路由表翻到最后才看得明白。
 *    日后往这里加页面时，先建页面再改 `ready`，别把顺序弄反。
 */
const navItems = [
  { name: 'adminUsers', path: '/admin/users', label: '用户管理', ready: true },
  { name: 'adminPackages', path: '/admin/packages', label: '套餐配置', ready: true },
  { name: 'adminTickets', path: '/admin/tickets', label: '工单管理', ready: true }
]

const activePath = computed(() => route.path)

/** 返回营业厅首页——不登出，管理员本来就能看营业厅（游客可见页）。 */
function handleBackToPortal() {
  router.push({ name: 'home' })
}

/**
 * 退出登录。
 *
 * ⚠️ **刻意不调 `reportAction('logout')`**，与 `ScreenLayout.vue` 的处理同理：
 *    角色表写明管理员「不产生任何行为数据」，`utils/tracker.js#send()` 那道
 *    角色闸本来就会把这条拦掉。写了一行永远不生效的上报代码，
 *    只会让后来者以为「管理后台有埋点」——那正是本项目花了很大力气
 *    划清的那条边界（十二个事件全部来自营业厅）。
 *
 * 也没有 `ElMessage.success('已退出登录')`：那是营业厅 `AppHeader` 的做法，
 * 当时退出后仍留在营业厅首页，需要一句反馈。这里退出后直接换页到登录页，
 * 页面变化本身就是反馈。
 */
async function handleLogout() {
  await auth.logout()
  router.replace({ name: 'login' })
}
</script>

<template>
  <div class="admin">
    <!--
      自建极简顶栏，**不复用 `AppHeader`**。理由两条：
        ① 那是营业厅的顶栏，导航项指向 `/`、`/packages`、`/promos`、`/user/dashboard`。
           管理员站在后台，把营业厅主导航顶在头上是噪音；而「我的账户」那一项
           对管理员本来就是灰的（`/user` 的 roles 只列 `USER`）。
        ② 它自带一个「退出登录」，与侧栏底部的退出重复。退出与返回
           **收敛到一处**，管理员只需要记一个地方。
    -->
    <header class="admin__top">
      <div class="admin__brand">
        <span class="admin__logo">电信</span>
        <span class="admin__title">管理后台</span>
      </div>
      <span class="admin__who">您好，{{ auth.displayName }}（系统管理员）</span>
    </header>

    <div class="admin__body">
      <aside class="admin__side">
        <el-menu :default-active="activePath" router class="admin__menu">
          <el-menu-item
            v-for="item in navItems"
            :key="item.path"
            :index="item.path"
            :disabled="!item.ready"
          >
            <span class="admin__menu-label">{{ item.label }}</span>
            <el-tag v-if="!item.ready" size="small" type="info" effect="plain">待开发</el-tag>
          </el-menu-item>
        </el-menu>

        <!-- 出入两条路收敛在这里，不在顶栏另放一份 -->
        <div class="admin__side-foot">
          <button class="admin__side-link" type="button" @click="handleBackToPortal">
            ← 返回营业厅
          </button>
          <button class="admin__side-link" type="button" @click="handleLogout">退出登录</button>
        </div>
      </aside>

      <main class="admin__main">
        <router-view />
      </main>
    </div>

    <!--
      复用 `AppFooter`：它没有导航，只有项目名与「当前为纯前端 + 假数据」两行字。
      后一条提示在管理后台**尤其**该出现——管理员在这一页看到的账号、套餐、工单
      全是假数据，而这一页又长得最像真的。
    -->
    <AppFooter />
  </div>
</template>

<style scoped>
.admin {
  display: flex;
  flex-direction: column;
  min-height: 100%;
}

.admin__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 60px;
  padding: 0 20px;
  border-bottom: 1px solid #e4e7ed;
  background: #fff;
}

.admin__brand {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.admin__logo {
  padding: 2px 8px;
  border-radius: 4px;
  background: var(--tp-primary);
  color: #fff;
  font-size: 15px;
  font-weight: 700;
}

.admin__title {
  font-size: 17px;
  font-weight: 600;
  letter-spacing: 1px;
}

.admin__who {
  color: var(--tp-text-sub);
  font-size: 13px;
}

.admin__body {
  display: flex;
  flex: 1;
  gap: 20px;
  /* 比用户端宽：管理端的表格列多（账号那一页有 9 列），1160px 会把列挤扁 */
  max-width: 1360px;
  width: 100%;
  margin: 0 auto;
  padding: 20px;
}

.admin__side {
  display: flex;
  flex: 0 0 200px;
  flex-direction: column;
  gap: 8px;
}

.admin__menu {
  border-right: none;
  border-radius: 6px;
  background: #fff;
}

.admin__menu-label {
  flex: 1;
}

.admin__side-foot {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  padding: 10px 20px;
}

.admin__side-link {
  padding: 0;
  border: none;
  background: none;
  color: var(--tp-text-sub);
  font-size: 13px;
  cursor: pointer;
}

.admin__side-link:hover {
  color: var(--tp-primary);
}

.admin__main {
  flex: 1;
  min-width: 0;
}
</style>
