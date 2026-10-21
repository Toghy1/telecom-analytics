package com.demo01.flink.sink;

import com.demo01.flink.config.JdbcConfig;
import com.demo01.flink.model.Event;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

/**
 * 干净事件写 {@code dwd_behavior_log}。
 *
 * <p>用 {@code ON DUPLICATE KEY UPDATE} 而不是裸 INSERT：作业重跑（尤其是
 * 没带 {@code --reset} 时）不会因为唯一键冲突整批失败，最终值也保持一致。
 * 唯一键是 {@code uk_event(event_id, event_date)}，与判脏规则⑥同口径。
 */
public class DwdSink extends AbstractJdbcBatchSink<Event> {

    private static final long serialVersionUID = 1L;

    public DwdSink(JdbcConfig config) {
        super(config);
    }

    /** 实时作业传 1：一条一提交，落库立即可见。 */
    public DwdSink(JdbcConfig config, int batchSize) {
        super(config, batchSize);
    }

    @Override
    protected String sql() {
        return "INSERT INTO dwd_behavior_log "
                + "(event_id, event_date, event_code, event_time, user_id, session_id, "
                + " page, region, device, ip, ext) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE event_code = VALUES(event_code), "
                + " event_time = VALUES(event_time), user_id = VALUES(user_id), "
                + " session_id = VALUES(session_id), page = VALUES(page), "
                + " region = VALUES(region), device = VALUES(device), "
                + " ip = VALUES(ip), ext = VALUES(ext)";
    }

    @Override
    protected void bind(PreparedStatement ps, Event e) throws SQLException {
        ps.setString(1, e.getEventId());
        ps.setDate(2, Date.valueOf(e.getEventDate()));
        ps.setString(3, e.getEventCode());
        // ⚠️ 用 setObject(LocalDateTime) 而不是 Timestamp.valueOf：
        //    后者按 JVM 默认时区解释再换算到 serverTimezone，Flink 跑在
        //    UTC 的 Linux 上会给 UTC 字面量再加 8 小时。event_time 存的是
        //    UTC 字面量，必须原样落库，不能受 JVM 时区影响。
        ps.setObject(4, e.getEventTime());
        if (e.getUserId() == null) {
            ps.setNull(5, Types.BIGINT);
        } else {
            ps.setLong(5, e.getUserId());
        }
        ps.setString(6, e.getSessionId());
        ps.setString(7, e.getPage());
        ps.setString(8, e.getRegion());
        ps.setString(9, e.getDevice());
        if (e.getIp() == null) {
            ps.setNull(10, Types.VARCHAR);
        } else {
            ps.setString(10, e.getIp());
        }
        if (e.getExt() == null) {
            ps.setNull(11, Types.VARCHAR);
        } else {
            ps.setString(11, e.getExt());
        }
    }
}
