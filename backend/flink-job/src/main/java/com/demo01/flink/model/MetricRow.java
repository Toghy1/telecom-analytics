package com.demo01.flink.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 一行离线指标，对应 {@code dws_metric_offline} 的一条记录。
 *
 * <p>唯一键是 {@code (metric_code, dimension, dimension_value, stat_date)}，
 * 写库用 {@code ON DUPLICATE KEY UPDATE}，所以作业重跑不会产生重复行。
 */
public class MetricRow implements Serializable {

    private static final long serialVersionUID = 1L;

    private String metricCode;
    private String dimension;
    private String dimensionValue;
    private LocalDate statDate;
    private BigDecimal metricValue;

    public MetricRow() {
    }

    public MetricRow(String metricCode, String dimension, String dimensionValue,
                     LocalDate statDate, BigDecimal metricValue) {
        this.metricCode = metricCode;
        this.dimension = dimension;
        this.dimensionValue = dimensionValue;
        this.statDate = statDate;
        this.metricValue = metricValue;
    }

    public String getMetricCode() {
        return metricCode;
    }

    public void setMetricCode(String metricCode) {
        this.metricCode = metricCode;
    }

    public String getDimension() {
        return dimension;
    }

    public void setDimension(String dimension) {
        this.dimension = dimension;
    }

    public String getDimensionValue() {
        return dimensionValue;
    }

    public void setDimensionValue(String dimensionValue) {
        this.dimensionValue = dimensionValue;
    }

    public LocalDate getStatDate() {
        return statDate;
    }

    public void setStatDate(LocalDate statDate) {
        this.statDate = statDate;
    }

    public BigDecimal getMetricValue() {
        return metricValue;
    }

    public void setMetricValue(BigDecimal metricValue) {
        this.metricValue = metricValue;
    }
}
