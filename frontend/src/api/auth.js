import request from './request'
import { DEMO_ACCOUNTS } from './mock/account'

/**
 * 登录。成功返回 `{ token, account }`。
 *
 * 入参 `{login_name, password}`，与后端 `POST /auth/login` 的 DTO 同名
 * （全局 SNAKE_CASE 策略负责与 Java 的 `loginName` 互转）。
 *
 * ⚠️ 登录标识**按角色不同**：电信用户填手机号，运营分析员 / 系统管理员填用户名。
 *    所以失败时一律报「账号或密码不正确」——不能再说「手机号或密码不正确」，
 *    那会误导分析员以为要输手机号。这句话由后端统一给出（不区分「用户不存在」
 *    与「密码错」，否则等于提供一个枚举账号的接口）。
 *
 * ⚠️ 返回的 `account.login_name` 是**脱敏态**（`138****1234`），
 *    拿着它再登录是登不上的——明文只存在于登录请求体里，不回落。
 *    脱敏由后端 Entity → VO 完成（选型⑥），前端不做任何加工。
 */
export function login(payload) {
  return request.post('/auth/login', payload)
}

/**
 * 退出登录。
 *
 * 后端会把 token 从白名单里摘掉——JWT 本身在过期前验签仍然通过，
 * 白名单才是「已登出」的唯一来源。
 */
export function logout() {
  return request.post('/auth/logout')
}

/**
 * 演示账号提示（登录页用）。
 *
 * ⚠️ 这是**唯一的 mock 残留**：三个账号的口令固定为 123456，把口令交给后端
 *    查询没有意义（真实系统也不会提供这种接口）。等演示阶段结束、登录页
 *    去掉提示块时，本函数与它对 `mock/account.js` 的引用一起删除。
 *
 * 只带出 `login_name`（登录入口的明文标识）与口令、角色，**不抛整个 account**：
 * account 里是脱敏后的展示值，拿它去登录是登不上的，两者混用正是要防的错。
 */
export function getDemoHint() {
  return DEMO_ACCOUNTS.map(({ login_name, password, account }) => ({
    login_name,
    password,
    role: account.role
  }))
}
