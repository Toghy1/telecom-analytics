/**
 * 当前时刻，项目统一的日期时间文本格式：`YYYY-MM-DD HH:mm:ss`。
 *
 * ⚠️ 抽这个函数出来不是为了少写几行，而是**这个格式必须全站唯一**。
 *    业务库的时间字段（`t_order.order_time`、`t_promo.start_time` / `end_time`、
 *    `t_ticket.create_time` / `handle_time`……）都按这个定宽格式存，
 *    比较时直接用 `localeCompare` / `<=`，结果与时间先后完全一致。
 *    格式一旦有第二种写法，两处时间就**没法比**——而且比不出来的时候不报错，
 *    只是判断结果悄悄变了。
 *
 * 为什么不 `new Date(...)` 互相比较：
 *   `new Date('2026-10-31 23:59:59')` 按**本地时区**解析，而
 *   `new Date('2026-10-31T23:59:59Z')` 按 **UTC** 解析——两种写法差 8 小时，
 *   活动在最后一天的晚上会显示成「已结束」。定宽字符串按位对齐，
 *   整个时区问题就不存在。后端对应的是 MySQL 的 `NOW()`，同样取服务器本地时区。
 *
 * 提取自 `api/promo.js` 的 `now()` 与 `api/order.js` 的 `nowText()`——两处实现
 * 逐字相同，只是名字不一样，写第三处（工单）时才发现。这种「必须处处一致」
 * 的东西，复制到第二遍就是隐患的开始。
 */
export function formatDateTime(d) {
  const p = (n) => String(n).padStart(2, '0')
  return (
    `${formatDate(d)} ` +
    `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
  )
}

/**
 * 只到日，`YYYY-MM-DD`。是上面那个格式的**前缀**，不是另一种格式。
 *
 * 离线指标的统计粒度就是「天」（`dws_metric_offline.stat_date`），
 * 留存曲线的横轴也是一天一个点。用 `formatDateTime(d).slice(0, 10)`
 * 也能拿到同样的串，但那是「先造全串再砍掉」，读的人要自己数位数；
 * 这里显式给一个日期格式，两者共用同一段补零逻辑。
 */
export function formatDate(d) {
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

/** 当前时刻，同一格式。 */
export function nowText() {
  return formatDateTime(new Date())
}

/**
 * 定宽日期串按天平移：`shiftDays('2026-09-30', -13)` → `'2026-09-17'`。
 *
 * 专门用来**从日历推出横轴**（留存曲线的近 14 天 cohort），而不是从
 * 查回来的数据行里推。两者的区别是实打实的：如果某天的 cohort 因为
 * 漏斗口径变化没有算出 `d7`，行里就没有这一条——按数据推横轴会把这天
 * 整个**抽掉**，图上看着是连续的，实际缺了一档；按日历推横轴，这天
 * 仍在轴上，只是值为 `null`，缺口看得见。见 `api/screen.js#getRetention`。
 *
 * ⚠️ 拼 `T00:00:00` 而不是裸 `new Date('2026-09-30')`：后者按 **UTC** 解析，
 *    在西半球会退到前一天，平移 13 天就整体错一位。拼上零点按**本地**解析，
 *    与库里存的本地日期对齐（同文件顶部的时区说明）。
 *    `setDate` 自己处理跨月跨年（9-30 减 13 天 = 9-17，1-3 减 5 天 = 上年 12-29）。
 */
export function shiftDays(dateText, days) {
  const d = new Date(`${dateText}T00:00:00`)
  d.setDate(d.getDate() + days)
  return formatDate(d)
}

/**
 * 按「向下取整到 N 分钟」把时刻对齐到时间桶。
 *
 * 大屏的实时趋势曲线（板块②）是**按桶聚合**的：近 1 小时 = 12 个 5 分钟桶，
 * 近 24 小时 = 24 个 1 小时桶。桶的边界必须对齐，否则后端算出来的
 * `stat_time` 与前端补的桶对不上——前端会以为来了个新桶，画出一条锯齿。
 *
 * ⚠️ 用 `setMinutes(0,0,0)` 逐位清零，不用「时间戳整除」。后者踩的是同一个
 *    时区坑：`Math.floor(Date.now()/300000)*300000` 按 UTC 取整，
 *    在东八区会整体偏 8 小时，桶边界落在 :02 / :07 / :12……看着像随机错位。
 */
export function floorToBucket(d, minutes) {
  const t = new Date(d.getTime())
  if (minutes >= 60) {
    const hours = Math.floor(minutes / 60)
    t.setMinutes(0, 0, 0)
    t.setHours(Math.floor(t.getHours() / hours) * hours)
  } else {
    t.setSeconds(0, 0)
    t.setMinutes(Math.floor(t.getMinutes() / minutes) * minutes)
  }
  return t
}
