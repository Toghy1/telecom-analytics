package com.demo01.analysis.screen;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * `dws_metric_offline` 的一行，形状与前端 `mock/screen.js#offlineRow` 一致。
 * 字段名保持 snake_case —— 全项目命名约定，前端不做 camelCase 转换。
 */
public record OfflineMetric(
        String metric_code,
        String dimension,
        String dimension_value,
        LocalDate stat_date,
        BigDecimal metric_value) {
}
