package com.demo01.flink.sink;

import com.demo01.flink.config.JdbcConfig;
import com.demo01.flink.model.DirtyRecord;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * 脏行写 {@code dwd_behavior_dirty}。
 *
 * <p>{@code source} 由调用方指定：离线作业写 {@code OFFLINE}，实时作业写
 * {@code REALTIME}。两个作业的七条规则同一份实现，但死信来源必须分得清，
 * 否则大屏和排查脚本都看不出这条脏数据是哪条链路发现的。
 *
 * <p>死信表没有业务唯一键，重跑会追加重复行——所以离线对拍必须带
 * {@code --reset}。这也是 etl.py 每次先 TRUNCATE 的原因。
 */
public class DirtySink extends AbstractJdbcBatchSink<DirtyRecord> {

    private static final long serialVersionUID = 1L;

    private final String source;

    /** 离线默认：来源 OFFLINE，1000 条一批。 */
    public DirtySink(JdbcConfig config) {
        this(config, "OFFLINE", DEFAULT_BATCH_SIZE);
    }

    public DirtySink(JdbcConfig config, String source) {
        this(config, source, DEFAULT_BATCH_SIZE);
    }

    /** 实时作业传 1：一条一提交。 */
    public DirtySink(JdbcConfig config, String source, int batchSize) {
        super(config, batchSize);
        this.source = source;
    }

    @Override
    protected String sql() {
        return "INSERT INTO dwd_behavior_dirty "
                + "(source, raw_content, fail_reason, fail_time) "
                + "VALUES (?, ?, ?, ?)";
    }

    @Override
    protected void bind(PreparedStatement ps, DirtyRecord d) throws SQLException {
        ps.setString(1, source);
        ps.setString(2, d.getRawContent());
        ps.setString(3, d.getFailReason());
        // 同 DwdSink：按字面量写，避免 JVM 时区（集群常是 UTC）把时间整体挪 8 小时。
        ps.setObject(4, d.getFailTime());
    }
}
