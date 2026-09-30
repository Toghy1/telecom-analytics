/**
 * 实时指标订阅（大屏板块①②的数据来源）。
 *
 * 本阶段是「纯前端 + 假数据」，所以这里的推送是**定时器伪造**的。
 * 但它刻意伪造得非常真——因为「假数据必须能证伪」这条纪律在这里的含义是：
 * **假实现必须与真实现的接口形状逐字一致**，否则接后端那天，页面得推倒重写。
 *
 * 真实实现（后端就绪后替换本文件，页面一行不动）：
 *
 *     import { fetchEventSource } from '@microsoft/fetch-event-source'
 *
 *     export function subscribeRealtime(onMessage) {
 *       const ctrl = new AbortController()
 *       fetchEventSource('/api/realtime/metrics', {
 *         headers: { Authorization: `Bearer ${getToken()}` },
 *         signal: ctrl.signal,
 *         onmessage(ev) { onMessage(JSON.parse(ev.data)) },
 *       })
 *       return { close: () => ctrl.abort() }
 *     }
 *
 * 三处刻意对齐的细节：
 *
 *   ① **返回 `{ close() }`**——`fetchEventSource` 没有返回值，靠 `AbortController`
 *      取消，所以真实现里要自己包一层 `{ close }`。这里先按最终的形状返回，
 *      页面在 `onUnmounted` 里调 `close()` 的写法就永远不用改。
 *
 *   ② **每次回调一条 `dws_metric_realtime` 记录**（`metric_code` /
 *      `metric_value` / `stat_time`），不是一个自定义的「大屏数据包」。
 *      SSE 那一头就是实时消费程序每算出一个指标落一行后立即推送，
 *      一条消息一行，前端按 `metric_code` 分发。若这里图省事推一个大对象，
 *      接后端时要改的是**每一个数字卡的取值路径**。
 *
 *   ③ **累计量只增不减。** `today_pv` / `today_uv` / `today_orders` /
 *      `today_tickets` 都是当日累计值，真实链路里它们只可能变大。这条不是
 *      废话——假实现里用随机游走生成累计量，很容易写出「数字跳回去」的效果，
 *      大屏上表现为数字卡莫名变小。这是**假数据独有的 bug**，真数据不会出现，
 *      所以必须在假实现里就堵掉。见下方 `bump()`。
 *
 * ⚠️ 不用浏览器原生 `EventSource`：它不支持自定义请求头，`Authorization`
 *    送不出去，token 只能塞进 URL，而 URL 会进 Nginx access log 与浏览器历史
 *    （选型⑤）。这一条在假实现阶段就已经决定了契约，不能等到接线时再说。
 */

import { formatDateTime } from '@/utils/datetime'

/** 推送间隔（毫秒）。真实后端由实时消费程序的计算周期决定。 */
const PUSH_INTERVAL = 2000

/**
 * 当前值。模块级单例——大屏同一时刻只有一个实例订阅，
 * 且数值要在多次订阅之间保持连续（用户切到子页再切回来，
 * 数字不该从头开始）。
 */
const state = {
  online_users: 386,
  today_pv: 18420,
  today_uv: 2740,
  today_orders: 415,
  today_tickets: 96
}

/** 推送轮转顺序。一次推一条，轮流推，避免每秒把所有指标刷一遍。 */
const CODES = ['online_users', 'today_pv', 'today_uv', 'today_orders', 'today_tickets']

let cursor = 0

/**
 * 确定性伪随机（与 `mock/screen.js` 同一个函数）。
 * 用函数而不是 `Math.random()`：验证时要能断言「这个数字在合理区间内」，
 * 纯随机只能断言「是个数」，等于没断言。
 */
function noise(seed) {
  const x = Math.sin(seed * 12.9898) * 43758.5453
  return x - Math.floor(x)
}

let tick = 0

/**
 * 生成下一条指标记录。
 *
 * `online_users` 是**瞬时量**（当前在线人数），它可以上下波动；
 * 其余四个是**累计量**，只增不减——靠 `bump()` 保证。
 */
function nextRecord() {
  tick++
  const code = CODES[cursor % CODES.length]
  cursor++

  const n = noise(tick * 7.31)

  if (code === 'online_users') {
    // 瞬时量：在 340–430 之间摆动，模拟用户在进出
    state.online_users = 340 + Math.round(n * 90)
  } else if (code === 'today_pv') {
    state.today_pv += 1 + Math.round(n * 8)
  } else if (code === 'today_uv') {
    state.today_uv += n > 0.55 ? 1 : 0
  } else if (code === 'today_orders') {
    state.today_orders += n > 0.82 ? 1 : 0
  } else if (code === 'today_tickets') {
    state.today_tickets += n > 0.93 ? 1 : 0
  }

  return {
    metric_code: code,
    metric_value: state[code],
    stat_time: statText()
  }
}

/**
 * `stat_time` 用项目统一的定宽格式。
 *
 * ⚠️ 走 `utils/datetime.js` 的 `formatDateTime`，**不在这里另写一份**。
 *    写成 `toLocaleString('sv-SE')` 也能凑出 `YYYY-MM-DD HH:mm:ss`，
 *    看着一样、跑着也一样——但那是**第二份格式实现**。日后日期格式要动
 *    （比如统一改成绩效口径的 `YYYY/MM/DD`），改了一处漏了一处，
 *    两次取到的时间就比不了，而且比不出来的时候不报错。
 */
function statText() {
  return formatDateTime(new Date())
}

/**
 * 订阅实时指标。
 *
 * @param {(record: {metric_code: string, metric_value: number, stat_time: string}) => void} onMessage
 * @returns {{ close: () => void }} 与真实实现（`fetchEventSource` 的 `AbortController` 包装）同形
 */
export function subscribeRealtime(onMessage) {
  const timer = setInterval(() => {
    const record = nextRecord()
    // 与 `utils/tracker.js` 同一套调试约定，便于在浏览器里观察推送。
    if (import.meta.env.DEV) {
      console.debug('[实时]', record)
    }
    onMessage(record)
  }, PUSH_INTERVAL)

  return {
    close() {
      clearInterval(timer)
    }
  }
}

/**
 * 实时指标的初始快照（`dws_metric_realtime` 当前值）。
 *
 * ⚠️ **必须与 `subscribeRealtime` 配套使用，不能只用其中一个。**
 *    SSE 推的是「变化」，订阅上之前的历史值它不会补发；只订不取初值，
 *    大屏打开后要等到第一次推送才有数字，数字卡会先空一格再跳出来。
 *    （真实实现里这也不是可选项——`EventSource` 重连后服务端只补最后一条，
 *    完整初值仍要单独拉。）
 */
export function fetchRealtimeSnapshot() {
  const stat_time = statText()
  const rows = CODES.map((code) => ({
    metric_code: code,
    metric_value: state[code],
    stat_time
  }))
  return Promise.resolve(rows)
}

/** 仅供自动化验证重置状态用；生产代码不要调。 */
export function __resetRealtimeState() {
  state.online_users = 386
  state.today_pv = 18420
  state.today_uv = 2740
  state.today_orders = 415
  state.today_tickets = 96
  cursor = 0
  tick = 0
}
