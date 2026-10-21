package com.demo01.realtime.metric;

/**
 * 页面热度的一行：`page` 今日被浏览多少次（板块②横向条形图）。
 *
 * 排序与 `LIMIT` 由 SQL 做，前端不再排——
 * 否则将来接口换个排序口径，图表会"顺手"排回降序，把改动掩盖掉。
 */
public record PageHeatRow(String page, long metric_value) {
}
