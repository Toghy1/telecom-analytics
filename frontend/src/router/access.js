/**
 * 「这个角色能不能进这个地址」——**全项目只有这一份判断**。
 *
 * 判断依据只有**路由表里的 `meta.requiresAuth` / `meta.roles`**（约定见
 * `router/index.js` 文件头），本文件不另存一份「谁能去哪」的清单。
 *
 * 为什么要单独抽出来，而不是各处直接写：除了 `beforeEach` 守卫，
 * 还有两处要问同一个问题——
 *
 *   1. `views/Login.vue`：登录后要不要把用户送回 `redirect` 指向的地址。
 *      不问的话，ANALYST 被 `/user/*` 拦下 → 登录 → 送回去 → 又被拦下，来回弹。
 *   2. `components/AppHeader.vue`：要不要显示「我的账户」入口。
 *      不问的话，ADMIN 在营业厅首页会看到一个点了就被弹回登录页的链接——
 *      **这种「点了弹走」的入口比没有入口更糟**，用户会以为是系统坏了。
 *
 * 若这两处各自写一遍角色判断，日后路由一改就会**悄悄失效**：不报错，
 * 只是把人送到不该去的地方，或者留一个永远点不通的入口。
 */

/**
 * @param {{ requiresAuth?: boolean, roles?: string[] }} meta 已解析路由的 `meta`
 * @param {string|undefined} role 当前角色；**未登录传 `undefined`**
 * @returns {boolean}
 */
export function isRoleAllowed(meta, role) {
  // 游客可见的页面（营业厅首页 / 套餐 / 活动）谁都能进，不看角色。
  // 漏斗第一段 `view_home` 正是游客产生的，这里一旦收口漏斗就断了。
  if (!meta?.requiresAuth) return true

  // 要登录而没登录。⚠️ 用 `role` 而不是「token 在不在」判断：
  // token 与 account 分别存在 localStorage 两个键下，任一个损坏都可能只剩另一个，
  // 而下面要比对的正是 account 里的角色——拿不到角色就是进不去。
  if (!role) return false

  // `roles` 缺省 = 不限角色（见 router/index.js 的 meta 约定），任何已登录角色都放行
  return !meta.roles || meta.roles.includes(role)
}
