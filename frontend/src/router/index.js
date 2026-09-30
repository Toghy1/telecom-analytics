import { createRouter, createWebHistory } from 'vue-router'
import PortalLayout from '@/layouts/PortalLayout.vue'
import UserLayout from '@/layouts/UserLayout.vue'
import { useAuthStore } from '@/stores/auth'
import { reportPageView } from '@/utils/tracker'
import { isRoleAllowed } from './access'

/**
 * 路由 meta 约定
 *
 *   title        浏览器标题
 *
 *   track        页面浏览类埋点事件编码，由下方 afterEach 守卫**自动上报**（选型⑦）。
 *                取值必须是《开发文档》§5.1.1 十二个事件之一。交互动作类
 *                （login / logout / order_package / submit_ticket）不写在这里，
 *                它们在动作发生的那一刻手动调用 reportAction()。
 *
 *   requiresAuth 需登录才能进（用户端页面）。
 *                营业厅首页是**游客可见**的，所以没有这一项——
 *                游客事件是行为漏斗的第一段，丢了漏斗就断了。
 *                用户端整体挂在 `/user` 下，这一项写在父路由上，
 *                vue-router 4 会把父子 meta 合并，子路由不用重复写。
 *
 *   roles        允许进入的角色（《开发文档》角色表：USER / ANALYST / ADMIN）。
 *                只在 `requiresAuth` 为真时有意义——它拦的是「**已登录但角色不对**」，
 *                游客可见的页面不写这一项，因为游客压根没有角色可比。
 *                与 requiresAuth 同理，写在父路由上靠 meta 合并生效。
 *
 *                ⚠️ **不写 `roles` 等于不限角色**（任何已登录角色都能进）。
 *                这是刻意的默认值而非遗漏：判断权集中在 `beforeEach` 一处，
 *                路由表只声明「谁能进」，不在这里散落 if。
 */
const routes = [
  {
    path: '/',
    component: PortalLayout,
    children: [
      {
        path: '',
        name: 'home',
        component: () => import('@/views/Home.vue'),
        meta: { title: '首页', track: 'view_home' }
      },
      // 游客端三个页面（claude.md「项目功能 · 营业厅 · 游客」）。
      // 都不带 requiresAuth——**游客能给系统贡献行为数据**，
      // 而这三个页面是漏斗的第一段（`view_home` → `login` → `query_*`），
      // 加了 requiresAuth 就等于把漏斗第一段掐掉。
      {
        path: 'packages',
        name: 'packages',
        component: () => import('@/views/Packages.vue'),
        meta: { title: '套餐资费', track: 'view_package_list' }
      },
      {
        path: 'promos',
        name: 'promos',
        component: () => import('@/views/Promos.vue'),
        meta: { title: '优惠活动', track: 'view_promo' }
      }
    ]
  },
  {
    path: '/user',
    component: UserLayout,
    redirect: { name: 'dashboard' },
    /**
     * ⚠️ `roles` 只列 `USER`，**没有把 `ADMIN` 放进来**，这一点想清楚了再说。
     *
     *    角色表写系统管理员属于「两者」（营业厅面 + 分析平台面），但那说的是**面**。
     *    这里这一片是**用户自助服务**——话费、账单、余量、套餐、报障，
     *    全是「查我自己的东西」，管理员进来看到的只会是 `mockAccount`
     *    （那个电信用户）的余额和账单，而不是他自己的。真后端要为管理员
     *    渲染这一片，得先有一套「管理员自己的 `t_account`」的语义，
     *    而那是管理后台的活，不是自助营业厅的活。
     *
     *    管理员的营业厅面入口是**尚未建成的管理后台**（账号管理 / 套餐配置 /
     *    工单处理），建成时会是 `/admin/*`，届时挂他自己的 `roles`。
     */
    meta: { requiresAuth: true, roles: ['USER'] },
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '账户总览', track: 'view_dashboard' }
      },
      {
        path: 'balance',
        name: 'balance',
        component: () => import('@/views/Balance.vue'),
        meta: { title: '话费查询', track: 'query_balance' }
      },
      {
        path: 'bills',
        name: 'bills',
        component: () => import('@/views/Bills.vue'),
        meta: { title: '账单查询', track: 'query_bill' }
      },
      {
        path: 'usage',
        name: 'usage',
        component: () => import('@/views/Usage.vue'),
        meta: { title: '余量查询', track: 'query_usage' }
      },
      {
        // ⚠️ 这一条**没有 `track`**，是刻意的。`order_package` 属**交互动作类**，
        //    编码由 Order.vue 在提交成功那一刻手动上报（选型⑦）。写进 meta.track
        //    的话，一进页面就记一次——用户只是来看看套餐，行为数据里却多了一次
        //    「办理」。而大屏漏斗的最后一段考的正是这个数。
        path: 'order',
        name: 'order',
        component: () => import('@/views/Order.vue'),
        meta: { title: '套餐办理' }
      },
      {
        // ⚠️ 同样**没有 `track`**——`submit_ticket` 是交互动作类，由 Ticket.vue
        //    在提交成功那一刻手动上报（选型⑦）。写进 meta.track 会让「打开报障页」
        //    被记成「提交了工单」，而大屏板块①的「今日新增工单」数字卡取的正是这个数。
        path: 'ticket',
        name: 'ticket',
        component: () => import('@/views/Ticket.vue'),
        meta: { title: '在线报障' }
      },
      {
        // 这一条**有 `track`**——`view_profile` 是页面浏览类（选型⑦），
        // 翻到这一页本身就是要记的行为，没有对应的动作可绑，交给守卫上报。
        // 与上面 order / ticket 两条正好相反。
        path: 'profile',
        name: 'profile',
        component: () => import('@/views/Profile.vue'),
        meta: { title: '个人信息', track: 'view_profile' }
      }
      // 用户端七个页面到此齐了（《开发文档》§6.4）。
      // 往后是管理后台与大屏，不再挂在这个布局下。
    ]
  },
  /**
   * 分析平台（运营分析员）。
   *
   * ⚠️ **这两条路由都没有 `meta.track`，这是刻意的，不是漏写。**
   *
   *    《开发文档》的角色表写明运营分析员「不产生行为数据」，
   *    决策「统一日志规范」的十二个事件里也**没有任何一个属于分析平台**。
   *    理由不只是「没定义」——若给大屏加一个 `view_screen`，
   *    运营分析员看大屏的行为就会进 `dwd_behavior_log`，而这份日志正是
   *    大屏自己的分析对象：**系统开始观察自己**，指标会被这个自我观察污染
   *    （大屏打开得越勤，PV 越高，而 PV 就是大屏上那块图）。
   *    十二个事件全部来自营业厅，这个边界必须守住。
   *
   * ⚠️ **`requiresAuth` 与 `roles` 已于 2026-09-30 补齐**——见 claude.md 决策
   *    「分析平台角色守卫与登录分流（问题 26 归档，改为现在就做）」。
   *    该决策把问题 26 的定性由「待后端就绪后收口」纠正为「假数据阶段现在就做全套」，
   *    因为三条收口动作**没有一条依赖后端**——账号在 `mock/account.js` 加两条即可。
   *
   *    大屏原先敢不加守卫，理由是它**没有任何用户个人明细**：最小必要原则下
   *    分析员看到的本来就全是聚合数据，少了鉴权暴露的只是「广东有 216 个用户」
   *    这类统计量；脱敏那条红线（手机号 / 身份证）与这里无关，那两个字段
   *    只出现在管理后台。这个理由**依然成立**，只是不再需要用它来顶替缺守卫了。
   *
   *    ⚠️ 加守卫的**前置条件是大屏得先有退出出口**，而它原先没有——
   *    带「退出登录」的 `AppHeader` 只挂在营业厅布局上，大屏头部只有页面自带的
   *    跳转链接（「系统运行分析 →」/「← 返回实时大屏」）。守卫一生效，
   *    `ANALYST` 登录即落在大屏，就成了**进得去出不来**，只能清 localStorage 换账号。
   *    退出出口已随本次一并补在 `ScreenLayout.vue`。
   */
  {
    path: '/screen',
    name: 'screen',
    component: () => import('@/views/screen/Screen.vue'),
    meta: { title: '用户行为分析', requiresAuth: true, roles: ['ANALYST', 'ADMIN'] }
  },
  {
    path: '/screen/pipeline',
    name: 'screenPipeline',
    component: () => import('@/views/screen/Pipeline.vue'),
    meta: { title: '系统运行分析', requiresAuth: true, roles: ['ANALYST', 'ADMIN'] }
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/Login.vue'),
    // 登录页本身不算「页面浏览类」事件——它是交互动作类 `login` 的载体，
    // 由 Login.vue 在登录成功那一刻手动上报。
    meta: { title: '登录' }
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

/**
 * 登录守卫 + 角色守卫。
 *
 * 「登没登录」与「角色对不对」是同一个问题的两半——都由 `isRoleAllowed()`
 * 看着 `meta` 一并回答，所以这里不必分两段、也没有「两支判断的先后顺序」可写错。
 *
 * ⚠️ 角色不符**按未登录处理**（送回登录页并带上目标地址），既不静默放行、
 *    也不跳 403 页——本项目没有 403 页，而「角色不对」在演示里的实际含义
 *    就是「你该从自己的入口进」。
 *
 * ⚠️ 带上 `redirect` 是为了让 `Login.vue` 能判断该不该原路送回：那边拿
 *    目标地址的 meta **再问一次同一个函数**（见 `resolveTarget()`），
 *    否则会出现「ANALYST 被 `/user/*` 拦下 → 登录 → 又被拦下」的来回弹。
 */
router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!isRoleAllowed(to.meta, auth.account?.role)) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  return true
})

// 选型⑦：页面浏览类事件由路由守卫统一上报，页面组件里一行上报代码都不用写
router.afterEach((to) => {
  if (to.meta.track) {
    reportPageView(to.meta.track, to.fullPath)
  }
  document.title = to.meta.title ? `${to.meta.title} · 电信自助营业厅` : '电信自助营业厅'
})

export default router
