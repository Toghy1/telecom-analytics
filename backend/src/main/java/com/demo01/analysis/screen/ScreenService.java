package com.demo01.analysis.screen;

import com.demo01.analysis.common.PageResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 两个页面需要的只读查询。没有写操作、没有事务——本阶段就是「查表」。
 */
@Service
public class ScreenService {

    /** 七条规则的枚举值，顺序与前端 `DIRTY_RULE` 一致。缺的补 0。 */
    private static final List<String> RULE_CODES = List.of(
            "MISSING_REQUIRED", "ENUM_OUT_OF_RANGE", "TYPE_ERROR",
            "TIME_ILLEGAL", "ID_FORMAT_ERROR", "DUPLICATE",
            "OVERLONG_GARBLED");

    private final JdbcTemplate jdbc;

    public ScreenService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /* ---------------- 大屏 · 离线指标 ---------------- */

    public List<OfflineMetric> offlineMetrics() {
        return jdbc.query("""
                SELECT metric_code, dimension, dimension_value, stat_date, metric_value
                FROM dws_metric_offline
                WHERE stat_date = (SELECT MAX(stat_date) FROM dws_metric_offline)
                ORDER BY id
                """, (rs, i) -> new OfflineMetric(
                rs.getString("metric_code"),
                rs.getString("dimension"),
                rs.getString("dimension_value"),
                rs.getDate("stat_date").toLocalDate(),
                rs.getBigDecimal("metric_value")));
    }

    /* ---------------- 子页 · 死信分页 ---------------- */

    public PageResult<DeadLetter> deadLetters(int page, int size, String source) {
        int current = Math.max(page, 1);
        int limit = Math.min(Math.max(size, 1), 100);
        boolean filtered = source != null && !source.isBlank();

        String where = filtered ? " WHERE source = ? " : " ";
        List<Object> countArgs = filtered ? List.of(source) : List.of();

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dwd_behavior_dirty" + where,
                Long.class, countArgs.toArray());
        long totalRows = total == null ? 0 : total;
        int pages = (int) ((totalRows + limit - 1) / limit);

        List<Object> args = new ArrayList<>(countArgs);
        args.add(limit);
        args.add((long) (current - 1) * limit);

        // 同一批次的 fail_time 是相同的批处理标记（文件日 00:05），
        // 所以批内顺序只能靠 id。这里用 id ASC：批内按写入顺序展示，
        // 第一页能看到好几种失败原因。若用 id DESC，造数脚本追加在文件
        // 末尾的「重复上报」会独占整页——七条规则里只看得到一条。
        List<DeadLetter> records = jdbc.query("""
                SELECT id, source, raw_content, fail_reason, fail_time
                FROM dwd_behavior_dirty
                """ + where + """
                ORDER BY fail_time DESC, id ASC
                LIMIT ? OFFSET ?
                """, (rs, i) -> new DeadLetter(
                rs.getLong("id"),
                rs.getString("source"),
                rs.getString("raw_content"),
                rs.getString("fail_reason"),
                rs.getTimestamp("fail_time").toLocalDateTime()),
                args.toArray());

        return new PageResult<>(totalRows, pages, current, records);
    }

    /* ---------------- 子页 · 管道聚合 ---------------- */

    public PipelineSummary summary() {
        LocalDate latest = jdbc.queryForObject(
                "SELECT MAX(event_date) FROM dwd_behavior_log", LocalDate.class);
        if (latest == null) {
            return new PipelineSummary(null, emptyRuleHits(),
                    List.of(), List.of());
        }

        long stored = countStored(latest, latest);
        long dirty = countDirty(latest, latest);
        long collected = stored + dirty;
        // 入库率是百分比：stored / collected × 100，保留 1 位小数。
        // 写成 ×1000 再除会得到 928.3 这种「看起来像数字」的错值。
        double passRate = collected == 0 ? 0
                : BigDecimal.valueOf(stored * 100.0 / collected)
                        .setScale(1, RoundingMode.HALF_UP)
                        .doubleValue();

        PipelineSummary.Today today = new PipelineSummary.Today(
                latest.toString(), collected, dirty, stored, passRate);

        return new PipelineSummary(today, ruleHits(latest),
                trend(latest, 7), trend(latest, 30));
    }

    private Map<String, Long> emptyRuleHits() {
        Map<String, Long> hits = new LinkedHashMap<>();
        RULE_CODES.forEach(code -> hits.put(code, 0L));
        return hits;
    }

    private Map<String, Long> ruleHits(LocalDate day) {
        Map<String, Long> hits = emptyRuleHits();
        jdbc.query("""
                SELECT fail_reason, COUNT(*) AS cnt
                FROM dwd_behavior_dirty
                WHERE fail_time >= ? AND fail_time < ?
                GROUP BY fail_reason
                """, rs -> {
                    hits.put(rs.getString("fail_reason"), rs.getLong("cnt"));
                }, day.atStartOfDay(), day.plusDays(1).atStartOfDay());
        return hits;
    }

    private long countStored(LocalDate from, LocalDate to) {
        Long n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dwd_behavior_log
                WHERE event_date BETWEEN ? AND ?
                """, Long.class, from, to);
        return n == null ? 0 : n;
    }

    private long countDirty(LocalDate from, LocalDate to) {
        Long n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dwd_behavior_dirty
                WHERE fail_time >= ? AND fail_time < ?
                """, Long.class, from.atStartOfDay(),
                to.plusDays(1).atStartOfDay());
        return n == null ? 0 : n;
    }

    /**
     * 截止 `latest` 的近 `days` 天脏数据占比。
     *
     * 横轴按**日历**生成，不按数据行——没有数据的日期留 `null`，
     * 与前端「离线指标横轴来自日历」的约定同源。
     */
    private List<PipelineSummary.TrendPoint> trend(LocalDate latest, int days) {
        LocalDate from = latest.minusDays(days - 1L);

        Map<LocalDate, Long> storedMap = new HashMap<>();
        jdbc.query("""
                SELECT event_date AS d, COUNT(*) AS cnt
                FROM dwd_behavior_log
                WHERE event_date BETWEEN ? AND ?
                GROUP BY event_date
                """, rs -> {
                    storedMap.put(rs.getDate("d").toLocalDate(), rs.getLong("cnt"));
                }, from, latest);

        Map<LocalDate, Long> dirtyMap = new HashMap<>();
        jdbc.query("""
                SELECT DATE(fail_time) AS d, COUNT(*) AS cnt
                FROM dwd_behavior_dirty
                WHERE fail_time >= ? AND fail_time < ?
                GROUP BY DATE(fail_time)
                """, rs -> {
                    dirtyMap.put(rs.getDate("d").toLocalDate(), rs.getLong("cnt"));
                }, from.atStartOfDay(), latest.plusDays(1).atStartOfDay());

        List<PipelineSummary.TrendPoint> points = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            LocalDate day = from.plusDays(i);
            long s = storedMap.getOrDefault(day, 0L);
            long d = dirtyMap.getOrDefault(day, 0L);
            long total = s + d;
            BigDecimal ratio = total == 0 ? null
                    : BigDecimal.valueOf(d * 100.0 / total)
                            .setScale(1, RoundingMode.HALF_UP);
            points.add(new PipelineSummary.TrendPoint(day.toString(), ratio));
        }
        return points;
    }
}
