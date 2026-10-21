package com.demo01.realtime.metric;

/**
 * SSE 推送的单条记录，**形状就是 `dws_metric_realtime` 的一行**
 * （决策「大屏采用 SSE」）。
 *
 * ⚠️ 字段名用下划线、不用 Java 惯用的驼峰：这个对象直接序列化后交给前端
 *    `applyRealtime(record)` 按 `record.metric_code` 取值。若为了"Java 风格"
 *    在这里做一次驼峰映射，前端假数据与真数据的字段名就不一致了——
 *    而那条假数据实现，正是为了让接线当天页面**一行不用改**。
 *
 * ⚠️ 一次推送**一条**，不是打成一个「大屏数据包」。五个数字卡各自按
 *    `metric_code` 分发；打成一个包会让「某个指标算慢了」变成「整屏卡住」。
 *
 * @param metric_code  指标编码，取值见 `MetricService.CODES`
 * @param metric_value 指标值（`online_users` 是瞬时量，其余四个是当日累计量）
 * @param stat_time    统计时刻，格式 `yyyy-MM-dd HH:mm:ss`，**北京时间**
 */
public record MetricRecord(String metric_code, long metric_value, String stat_time) {
}
