package com.demo01.analysis.screen;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 子页「系统运行分析」一次取全的聚合数据。
 *
 * 口径：
 *   - `today`     —— **最新一个批次日**（ETL 处理的最新文件日），不是自然今天。
 *                    离线数据到 T-1，用自然日期会得到全 0。
 *   - `ruleHits`  —— 该批次日的七条规则命中数，缺失的规则补 0（横轴来自常量）。
 *   - `trendXd`   —— 截止统计日的近 N 天；没有数据的日期 `metric_value` 为
 *                    null，**不补 0**——「没跑」与「零脏数据」不是一回事。
 */
public record PipelineSummary(
        Today today,
        Map<String, Long> ruleHits,
        List<TrendPoint> trend7d,
        List<TrendPoint> trend30d) {

    public record Today(
            String date,
            long collected,
            long dirty,
            long stored,
            double passRate) {
    }

    public record TrendPoint(String stat_date, BigDecimal metric_value) {
    }
}
