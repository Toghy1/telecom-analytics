import { DEMO_ACCOUNTS } from '@/api/mock/account'

/**
 * 登录。成功返回 { token, account }。
 *
 * 本阶段用假数据实现：登录标识与口令在 DEMO_ACCOUNTS 里对得上才放行。
 * 这样做是为了让登录页的**失败分支**也能被真实触发——只走成功路径的样板页不算跑通。
 *
 * ⚠️ 登录标识**按角色不同**：电信用户填手机号，运营分析员 / 系统管理员填用户名
 *    （见 `mock/account.js` 顶部说明）。所以这里一律报「账号或密码不正确」——
 *    不能再说「手机号或密码不正确」，那会误导分析员以为要输手机号。
 *
 * 后端就绪后，整个函数体换成：
 *     return request.post('/auth/login', payload)
 */
export function login(payload) {
  const { login_name, password } = payload
  const matched = DEMO_ACCOUNTS.find(
    (item) => item.login_name === login_name && item.password === password
  )

  if (!matched) {
    return Promise.reject(new Error('账号或密码不正确'))
  }

  return Promise.resolve({
    token: `mock-token-${Date.now()}`,
    // 展开一份副本，避免页面直接改到 mock 模块里的对象
    account: { ...matched.account }
  })
}

/** 退出登录。后端就绪后换成 request.post('/auth/logout')。 */
export function logout() {
  return Promise.resolve(true)
}

/**
 * 假数据阶段专用：把演示账号交给登录页做提示。
 * 接入真实后端后，这个函数连同 mock 目录一起删除。
 *
 * ⚠️ 只带出 `login_name`（**登录入口的明文标识**——手机号或用户名）、口令与角色，
 *    不把整个 `account` 抛出去。`account.login_name` 是脱敏后的展示值，
 *    拿它去登录是登不上的，两者混用正是上面那条注释要防的错。
 */
export function getDemoHint() {
  return DEMO_ACCOUNTS.map(({ login_name, password, account }) => ({
    login_name,
    password,
    role: account.role
  }))
}
