/**
 * 埋点上报接口（`POST /api/track`，由 business-service 8082 承接）。
 *
 * ⚠️ **刻意不走 `api/request.js` 那个 axios 实例**，理由有三条：
 *
 *   ① 那个实例的响应拦截器会为**每个失败**弹一次 `ElMessage`。
 *      埋点是旁路的、每次路由切换都发一条的请求——后端没起时，
 *      用户每点一下页面就弹一次红条，正常操作被埋点的故障污染。
 *   ② 它给所有请求带 `Authorization`，而埋点接口对游客开放（漏斗第一段
 *      就是游客产生的），不该被登录态牵连。
 *   ③ `keepalive` 是 fetch 的能力，axios 没有等价开关——而 `logout`
 *      埋点发完之后页面马上跳转，不 keepalive 的请求会被浏览器取消，
 *      这一条永远到不了后端，会话结束的判据就断了。
 *
 * 分层纪律没破：页面与 `utils/tracker.js` 都不直接写 URL，请求仍然只在
 * `src/api/` 里出现。
 */

const TRACK_URL = '/api/track'

/**
 * 上报一条行为事件。**即发即忘**，不返回 Promise。
 *
 * 失败一律静默：埋点丢几条可以接受，为它弹提示或重试会干扰用户操作，
 * 真出问题去看 business-service 的日志与 Kafka 的 `behavior-log`。
 */
export function postTrack(payload) {
  try {
    fetch(TRACK_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
      keepalive: true
    }).catch(() => {})
  } catch {
    // 老浏览器不支持 keepalive 以外的情形；同样静默。
  }
}
