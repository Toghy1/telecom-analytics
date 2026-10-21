package com.demo01.flink.sink;

import com.demo01.flink.config.JdbcConfig;
import com.demo01.flink.model.MetricRow;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * 离线指标写 {@code dws_metric_offline}。
 *
 * <p>唯一键 {@code uk_metric(metric_code, dimension, dimension_value, stat_date)}
 * + upsert：重跑覆盖同一统计日的同一指标，不产生重复行。
 */
public class MetricSink extends AbstractJdbcBatchSink<MetricRow> {

    private static final long serialVersionUID = 1L;

    public MetricSink(JdbcConfig config) {
        super(config);
    }

    @Override
    protected String sql() {
        return "INSERT INTO dws_metric_offline "
                + "(metric_code, dimension, dimension_value, stat_date, metric_value) "
                + "VALUES (?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE metric_value = VALUES(metric_value)";
    }

    @Override
    protected void bind(PreparedStatement ps, MetricRow row) throws SQLException {
        ps.setString(1, row.getMetricCode());
        ps.setString(2, row.getDimension());
        ps.setString(3, row.getDimensionValue());
        ps.setDate(4, Date.valueOf(row.getStatDate()));
        ps.setBigDecimal(5, row.getMetricValue());
    }
}
