package com.demo01.realtime.metric;

/**
 * 访问趋势的一个桶（板块②折线图的一个点）。
 *
 * `stat_time` 是**桶的起点**，格式 `yyyy-MM-dd HH:mm:ss`（北京时间）。
 * 前端按同一套「向下取整到桶」的规则累加 SSE 增量，所以这里的桶宽
 * 必须与前端 `BUCKET_MINUTES` 一致：1 小时视图 5 分钟，24 小时视图 60 分钟。
 */
public record TrendPoint(String stat_time, long metric_value) {
}
