package com.demo01.flink.clean;

import com.demo01.flink.config.JdbcConfig;
import com.demo01.flink.io.JdbcSupport;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 实时作业的规则⑥判据：有界内存 + 查库。
 *
 * <p>为什么不能像离线那样只用一个内存 Set：实时作业要连续跑几天甚至几周，
 * {@code (event_id, event_date)} 只增不减，最终会把 TaskManager 内存吃光。
 * 也不能只查库：一条事件从进入 Kafka 到 sink 提交有延迟，同一 {@code event_id}
 * 的两条消息可能前后脚到达，第二条查库时第一条还没提交——这时靠最近集合兜底。
 *
 * <p>组合起来：
 * <ul>
 *   <li>最近 {@value #MAX_RECENT} 条 key 放在 LRU 里，挡住「同一批内重复」；</li>
 *   <li>更早的 key 查 {@code dwd_behavior_log}——已经提交的行都在库里；</li>
 *   <li>{@link #remember} 在规则⑥通过后立即调用（早于规则⑦），与 etl.py 同序。</li>
 * </ul>
 *
 * <p>⚠️ 单并行度：本类只有一个 JDBC 连接、一个非线程安全的 LRU。校验算子
 * 固定 {@code parallelism=1}，与离线作业的理由相同——「第一条」需要顺序。
 */
public class DbDuplicateChecker implements BehaviorValidator.DuplicateChecker, AutoCloseable {

    /** 最近 key 的上限。按 500 用户、每人每天几十条估算，够覆盖一整天。 */
    private static final int MAX_RECENT = 100_000;

    private final Connection conn;
    private final PreparedStatement ps;
    private final Map<String, Boolean> recent;

    public DbDuplicateChecker(JdbcConfig config) throws Exception {
        this.conn = JdbcSupport.connect(config);
        this.ps = conn.prepareStatement(
                "SELECT 1 FROM dwd_behavior_log "
                        + "WHERE event_id = ? AND event_date = ? LIMIT 1");
        this.recent = new LinkedHashMap<String, Boolean>(1024, 0.75f, false) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > MAX_RECENT;
            }
        };
    }

    @Override
    public boolean isDuplicate(String eventId, LocalDate eventDate) {
        String key = key(eventId, eventDate);
        if (recent.containsKey(key)) {
            return true;
        }
        try {
            ps.setString(1, eventId);
            ps.setDate(2, Date.valueOf(eventDate));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    // 库里已有：顺手放进最近集合，后面的同 key 不用再查。
                    recent.put(key, Boolean.TRUE);
                    return true;
                }
            }
            return false;
        } catch (SQLException e) {
            // 查不了库就无法保证去重，宁可让作业失败重启，也不要静默放行。
            throw new IllegalStateException("规则⑥查库失败：" + e.getMessage(), e);
        }
    }

    @Override
    public void remember(String eventId, LocalDate eventDate) {
        recent.put(key(eventId, eventDate), Boolean.TRUE);
    }

    private static String key(String eventId, LocalDate eventDate) {
        return eventId + '\u0000' + eventDate;
    }

    @Override
    public void close() {
        try {
            ps.close();
        } catch (SQLException ignored) {
            // 关闭失败不影响作业退出。
        }
        try {
            conn.close();
        } catch (SQLException ignored) {
            // 同上。
        }
    }
}
