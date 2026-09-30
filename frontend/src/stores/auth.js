import { defineStore } from 'pinia'
import { login as loginApi, logout as logoutApi } from '@/api/auth'

const TOKEN_KEY = 'demo01_token'
const USER_KEY = 'demo01_user'

/**
 * 登录态。
 *
 * 命名约定：**从接口来 / 往接口去的字段一律 `snake_case`**（选型⑤纪律，
 * 与后端实体、SQL 列、行为日志全链路同名）；**纯 JS 侧的标识符**
 * （state 名、getter 名、方法名）用 camelCase。
 * 这条纪律约束的是跨接口的 JSON，不是 JS 变量名。
 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    account: readStoredAccount()
  }),

  getters: {
    isLoggedIn: (state) => Boolean(state.token),

    /** account.login_name 拿到的就是脱敏态，前端直接显示，不做任何加工 */
    displayName: (state) => state.account?.login_name || ''
  },

  actions: {
    async login(payload) {
      const data = await loginApi(payload)
      this.token = data.token
      this.account = data.account
      localStorage.setItem(TOKEN_KEY, this.token)
      localStorage.setItem(USER_KEY, JSON.stringify(this.account))
      return data
    },

    /**
     * 本地账号信息打补丁。
     *
     * 用在「办理套餐成功后」这类场景：真后端此时会写库，前端随后重新拉一次账号
     * 信息（`GET /account`），本地那份就更新了。这里用打补丁顶替那次往返——
     * 假数据阶段没有那个接口可拉。
     *
     * ⚠️ **只改内存，不落 localStorage**，这一点是刻意的。
     *    `submitOrder()` 改的是 `mockAccount`，那是模块内存态，**刷新即还原**
     *    （等于数据库回到种子数据）。若这里把补丁也落盘，刷新后就只剩登录态
     *    是新套餐，而 `mockAccount` 已退回旧套餐——账户总览显示「孝心卡 19 元」，
     *    余量却按 79 元套餐的 60GB 算，两处自相矛盾，且没有任何报错。
     *    不落盘则刷新后两边一起回到种子数据，始终一致。
     */
    syncAccount(patch) {
      if (!this.account) return
      this.account = { ...this.account, ...patch }
    },

    async logout() {
      try {
        await logoutApi()
      } finally {
        // 后端登出失败也要把本地清干净，否则用户会卡在「点了没反应」
        this.token = ''
        this.account = null
        localStorage.removeItem(TOKEN_KEY)
        localStorage.removeItem(USER_KEY)
      }
    }
  }
})

function readStoredAccount() {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}
