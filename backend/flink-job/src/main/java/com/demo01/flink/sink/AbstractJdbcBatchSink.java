package com.demo01.flink.sink;

import com.demo01.flink.config.JdbcConfig;
import com.demo01.flink.io.JdbcSupport;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * 按批写 MySQL 的 sink 基类。
 *
 * <p>为什么不用 flink-connector-jdbc：这里只有三条固定 SQL，自己包一层
 * PreparedStatement 批处理就够了；少一个连接器，也少一处版本对齐
 * （JDBC connector 的版本号要跟 Flink 小版本严格搭配）。
 *
 * <p>批大小由子类决定：离线一次 1000 条（吞吐优先）；实时一次 1 条
 * （落库即提交，和原来 Spring 消费端 `ack-mode: record` 的可见性一致，
 * 也让大屏的今日累计量不用等批）。
 *
 * <p>作业重跑时靠各表自己的唯一键 + upsert 做幂等，不依赖 sink 的
 * exactly-once。
 */
public abstract class AbstractJdbcBatchSink<T> extends RichSinkFunction<T> {

    private static final long serialVersionUID = 1L;

    /** 离线默认批大小。 */
    protected static final int DEFAULT_BATCH_SIZE = 1000;

    private final JdbcConfig config;
    private final int batchSize;

    private transient Connection conn;
    private transient PreparedStatement ps;
    private transient int pending;

    protected AbstractJdbcBatchSink(JdbcConfig config) {
        this(config, DEFAULT_BATCH_SIZE);
    }

    protected AbstractJdbcBatchSink(JdbcConfig config, int batchSize) {
        this.config = config;
        this.batchSize = batchSize;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        conn = JdbcSupport.connect(config);
        conn.setAutoCommit(false);
        ps = conn.prepareStatement(sql());
    }

    @Override
    public void invoke(T value, Context context) throws Exception {
        bind(ps, value);
        ps.addBatch();
        pending++;
        if (pending >= batchSize) {
            flush();
        }
    }

    @Override
    public void close() throws Exception {
        try {
            flush();
        } finally {
            if (ps != null) {
                ps.close();
            }
            if (conn != null) {
                conn.close();
            }
        }
    }

    private void flush() throws SQLException {
        if (pending == 0) {
            return;
        }
        try {
            ps.executeBatch();
            conn.commit();
        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException ignored) {
                // 保留原始异常；rollback 失败通常说明连接已经断了。
            }
            throw e;
        } finally {
            pending = 0;
        }
    }

    /** 目标表的 INSERT 语句。 */
    protected abstract String sql();

    /** 把一行对象绑定到 PreparedStatement。 */
    protected abstract void bind(PreparedStatement ps, T value) throws SQLException;
}
