/**
 * 实时指标订阅（大屏板块①②的数据来源）。
 *
 * 2026-10-21 起接真后端：`realtime-service`（8081）每 2 秒算一遍五个指标，
 * 每条推一次 SSE。原来这里的定时器假数据已删除——**页面一行没改**，
 * 因为当初假实现就是按真接口的形状写的（返回 `{ close() }`、每次回调一条
 * `{metric_code, metric_value, stat_time}`）。这正是那条假数据纪律的意义。
 *
 * 三处不能省的细节：
 *
 *   ① **不用浏览器原生 `EventSource`**：它不支持自定义请求头，
 *      `Authorization` 送不出去，token 只能塞进 URL，而 URL 会进 Nginx
 *      access log 与浏览器历史（选型⑤）。这里用 `@microsoft/fetch-event-source`。
 *
 *   ② **返回 `{ close() }`**：库本身返回的是 Promise，没有 close 方法，
 *      取消靠 `AbortController`，所以必须自己包一层。页面在 `onBeforeUnmount`
 *      里调的就是它。
 *
 *   ③ **必须先拉快照、再订阅**（见 `fetchRealtimeSnapshot`）。SSE 只推变化，
 *      不补历史；只订不拉初值，数字卡会先空一格再跳出来。
 */

import { fetchEventSource } from '@microsoft/fetch-event-source'
import request from './request'

const TOKEN_KEY = 'demo01_token'

/** 断线重连间隔（毫秒）。后端重启时大屏要自己回来，不用手动刷新。 */
const RECONNECT_DELAY = 3000

function authHeaders() {
  const token = localStorage.getItem(TOKEN_KEY)
  // 与 axios 拦截器同一套约定。目前 realtime 接口不校验 token，
  // 但契约先带上——日后网关统一鉴权时不用再回来改这一处。
  return token ? { Authorization: `Bearer ${token}` } : {}
}

/**
 * 订阅实时指标。
 *
 * @param {(record: {metric_code: string, metric_value: number, stat_time: string}) => void} onMessage
 * @returns {{ close: () => void }}
 */
export function subscribeRealtime(onMessage) {
  const ctrl = new AbortController()
  let reconnectTimer = null

  function connect() {
    fetchEventSource('/api/realtime/metrics', {
      headers: authHeaders(),
      signal: ctrl.signal,

      onmessage(ev) {
        if (!ev.data) return
        try {
          onMessage(JSON.parse(ev.data))
        } catch (err) {
          // ⚠️ 回调里抛出的异常会被库当成「连接出错」并触发重连，
          //    一条格式不对的推送就会让大屏陷入"断开-重连"的循环。
          //    所以这里必须自己吞掉，只留一声控制台记录。
          console.warn('[实时] 推送内容无法处理：', err, ev.data)
        }
      },

      onerror(err) {
        // 我们自己调 close() 引发的中断不算错误，抛出去让库停下；
        // 其余（后端没起、网络抖动）返回一个间隔，库会等着重连。
        if (ctrl.signal.aborted) throw err
        return RECONNECT_DELAY
      },

      onclose() {
        // 服务端正常关流（例如 realtime-service 重启）时库不会重连，只会结束。
        // 大屏是常驻页面，必须自己接上——否则后端重启一次，大屏就永远停在旧数字上。
        if (ctrl.signal.aborted) return
        reconnectTimer = setTimeout(connect, RECONNECT_DELAY)
      }
    }).catch(() => {
      // `close()` 之后 Promise 会以「正常结束」resolve，这里只兜住
      // onerror 里主动抛出的那一种，不需要额外处理。
    })
  }

  connect()

  return {
    close() {
      ctrl.abort()
      if (reconnectTimer) {
        clearTimeout(reconnectTimer)
        reconnectTimer = null
      }
    }
  }
}

/**
 * 实时指标的初始快照（`GET /api/realtime/snapshot`）。
 *
 * ⚠️ **必须与 `subscribeRealtime` 配套使用，不能只用其中一个。**
 *    理由见文件头第③条。
 */
export function fetchRealtimeSnapshot() {
  return request.get('/realtime/snapshot')
}
