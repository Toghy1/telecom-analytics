/** 金额统一保留两位小数 */
export function yuan(value) {
  if (value === null || value === undefined) return '--'
  return Number(value).toFixed(2)
}

/**
 * 用量数值：保留有效小数，**不补零**。
 *
 * 与 `yuan()` 的区别就在这里——金额按分记账，59.8 元要显示成 `59.80`；
 * 而用量是「已用 48.75 GB / 452 分钟 / 118 条」，`48.75` 和 `452` 该什么样就什么样，
 * 一律补成 `452.00` 反而不像话。两个格式化函数各管一摊，不要合并。
 */
export function quantity(value) {
  if (value === null || value === undefined) return '--'
  return String(Number(value))
}

/** 日期时间只取到日，例如 '2026-09-01 00:05:12' → '2026-09-01' */
export function dateOnly(value) {
  return value ? String(value).slice(0, 10) : '--'
}

/**
 * 大数字千分位，例如 18420 → `18,420`。大屏数字卡用。
 *
 * ⚠️ `null` / `undefined` 返回 `'--'` 而**不是 `'0'`**，与上面三个函数一致。
 *    这条在大屏上尤其要紧：数字卡的初值来自 `getRealtimeSnapshot()`，
 *    在它返回之前值是 `undefined`。若格式化成 `0`，
 *    大屏打开的头一瞬间会显示「当前在线 0 人」——一个真实存在、且很吓人的读数。
 *    `--` 表示「还不知道」，`0` 表示「确实是零」，两者在大屏上必须是两种样子。
 */
export function thousands(value) {
  if (value === null || value === undefined) return '--'
  return Number(value).toLocaleString('en-US')
}
