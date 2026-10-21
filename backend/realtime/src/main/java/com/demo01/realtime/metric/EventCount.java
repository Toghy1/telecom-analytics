package com.demo01.realtime.metric;

/**
 * 今日某个 `event_code` 的发生次数（板块②环形图的数据源）。
 *
 * ⚠️ 后端返回的是**12 个事件**的计数，不是 6 个类别的计数：类别映射
 *    （`EVENT_CATEGORY`）是前端的口径，且环形图的横轴必须来自常量
 *    （`EVENT_CATEGORY_LABEL`）——某类本期为 0 时要在图上占一格，
 *    不能整个消失。聚合放在 `api/screen.js` 做，见那里的注释。
 */
public record EventCount(String event_code, long metric_value) {
}
