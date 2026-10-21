/**
 * 埋点上报（选型⑦）。
 *
 * 事件共 12 个，分两类（《开发文档》§5.1.1）：
 *
 *   ① 页面浏览类（8 个）—— 由**路由守卫自动上报**，见 src/router/index.js
 *      view_home / view_package_list / view_promo / view_dashboard /
 *      query_balance / query_bill / query_usage / view_profile
 *
 *   ② 交互动作类（4 个）—— 由**页面手动调用** reportAction()
 *      login / logout / order_package / submit_ticket
 *
 * 公共字段 10 个，见《开发文档》§5.1.2。
 *
 * ⚠️ 除此之外还有一道**角色闸**：角色表写明运营分析员与系统管理员
 *    「不产生行为数据」，这两类角色的一切事件在 `send()` 处统一丢弃。
 *    闸门只有一处，见下方 `producesBehaviorData()`——加新的事件上报时
 *    **不需要**在调用点判断角色。
 */

import { postTrack } from '@/api/track'

const USER_KEY = 'demo01_user'
const SESSION_KEY = 'demo01_session_id'

/**
 * 游客的兜底地域（游客没有账号，取不到归属地）。
 * 登录用户不取这个值，取 `t_account.region`——见下方 getRegion()。
 */
const DEFAULT_REGION = '上海市'

/** UUID 生成。浏览器非安全上下文下 `crypto.randomUUID` 可能不可用，故留兜底。 */
function uuid() {
  if (window.crypto?.randomUUID) {
    return window.crypto.randomUUID()
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}

/**
 * 会话 ID。
 * §5.1.3 明确「由前端生成（UUID 存 `sessionStorage`），后端不参与」，
 * 它是漏斗与留存的最小单位——**游客事件也靠它标识**，所以不能用登录态推导。
 */
function getSessionId() {
  let sid = sessionStorage.getItem(SESSION_KEY)
  if (!sid) {
    sid = uuid()
    sessionStorage.setItem(SESSION_KEY, sid)
  }
  return sid
}

function detectDevice() {
  const ua = navigator.userAgent || ''
  if (/Mobile|Android|iPhone|iPad|iPod/i.test(ua)) return 'MOBILE'
  if (/Windows|Macintosh|Mac OS X|Linux/i.test(ua)) return 'PC'
  return 'OTHER'
}

/**
 * 本地登录态里的账号对象。
 * 登录成功后 auth store 会把 account 落进 localStorage，`user_id` 与 `region`
 * 都从这里取——同一个来源，不会出现两个字段打架的情况。
 */
function getAccount() {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

/** 登录用户 ID；游客为 null（§5.1.2：`user_id` 可空，但游客事件照样入库）。 */
function getUserId() {
  return getAccount()?.id ?? null
}

/**
 * 地域。
 *
 * ⚠️ 浏览器**拿不到**用户所在城市——没有权限、没有接口，除非去调第三方 IP
 *    定位服务，而那又变回「做 IP 解析」，与 §5.1.3 已定的「region 由埋点端
 *    携带，不做 IP 解析」正好相反。所以必须落在一个前端拿得到的真值上。
 *
 * 2026-09-30 定稿的取值方式（claude.md 决策「埋点 `region` 取值方式定稿」）：
 *
 *     登录用户 → `t_account.region`（号码归属地，真实字段）
 *     游客     → DEFAULT_REGION 兜底
 *
 * **代价**：前端采到的 region 实际上只有一个取值，"哪个地区更爱办套餐"这类
 * 分析在真实事件上做不出来。这对本项目不构成问题——行为日志的主体来自批量
 * 造数脚本，脚本按不同地区随机分布即可喂饱大屏地域图；前端这几十条真实事件
 * 带什么 region，对全局分布没有影响。
 */
function getRegion() {
  return getAccount()?.region || DEFAULT_REGION
}

/**
 * 该角色的行为**该不该入库**。
 *
 * ⚠️ 这是《开发文档》角色表的**硬口径**，不是可选开关：
 *
 *     游客 ✅ ／ 电信用户 ✅ ／ 运营分析员 ❌ ／ 系统管理员 ❌
 *
 * **为什么放在 `send()` 这唯一出口，而不是散在各个调用点**：
 * 上报入口现在就有三个——路由守卫的页面浏览、`Login.vue` 的 `login`、
 * `AppHeader.vue` 的 `logout`——将来还有 `order_package` / `submit_ticket`。
 * 逐个加判断迟早会漏一个，而漏掉的那条若正好是 `login`（行为漏斗的**第二段**），
 * 后果是分析员登录被算进漏斗计数：数据上**看不出任何异常**，
 * 只会觉得转化率莫名高一点，几乎不可能反查到。
 *
 * ⚠️ 游客（`account` 拿不到）算 ✅——漏斗第一段 `view_home` 正是游客产生的，
 *    漏了它整条漏斗就断了。
 *
 * ⚠️ 调用时机也满足这个判据：`reportAction('login')` 在 `auth.login()` **之后**、
 *    `reportAction('logout')` 在 `auth.logout()` **之前**，两处 localStorage 里
 *    都已写着当前账号，角色取得到。
 */
function producesBehaviorData() {
  const role = getAccount()?.role
  return !role || role === 'USER'
}

function buildEvent(event_code, page, ext = null) {
  return {
    event_id: uuid(), // UUID，去重键（脏数据规则 6 靠它判重复）
    event_code,
    event_time: new Date().toISOString(), // ISO8601，毫秒精度
    user_id: getUserId(),
    session_id: getSessionId(),
    page: page || window.location.pathname,
    region: getRegion(),
    device: detectDevice(),
    ip: null, // 前端取不到本机公网 IP，且已约定不做 IP 解析
    ext
  }
}

function send(payload) {
  // 先过角色闸：运营分析员与系统管理员不产生行为数据（角色表）。
  if (!producesBehaviorData()) return

  // 「埋点直发 Kafka」：business-service 只检查 JSON 可解析性，不做业务校验，
  // 内容有问题的数据一律放行，由 realtime-service 按 7 条规则判脏后进死信表。
  // 即发即忘——埋点失败不能影响用户操作，详见 `api/track.js`。
  postTrack(payload)

  if (import.meta.env.DEV) {
    console.debug('[埋点]', payload)
  }
}

/** 页面浏览类事件——不要手动调用，由路由守卫统一触发。 */
export function reportPageView(event_code, page) {
  send(buildEvent(event_code, page))
}

/** 交互动作类事件——在动作发生的那一刻手动调用，`ext` 可带套餐 ID、工单 ID 等。 */
export function reportAction(event_code, ext = null) {
  send(buildEvent(event_code, window.location.pathname, ext))
}
