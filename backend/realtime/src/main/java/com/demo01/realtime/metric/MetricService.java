package com.demo01.realtime.metric;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 实时指标的算法（《开发文档》§7.1 的五个指标）。
 *
 * <p>本服务**不再消费 Kafka**：Flink 的 {@code RealtimeJob} 负责消费、洗净、
 * 写 {@code dwd_behavior_log}；这里只从库里取数、每 2 秒推一次 SSE。
 * 因此重启服务不会让累计量“跳回去”——数字本来就每次从库里数出来。
 *
 * 口径（有偏离的只有第一条，原因就在那里）：
 *
 *   - `online_users`  近 5 分钟内有事件的不同 `session_id` 数。
 *     ⚠️ **偏离文档**：§7.1 写的是「Redis 中有效 token 数」。本项目不做
 *     token 续期心跳，Redis 里放的是登录 token，游客根本不在里面——
 *     而大屏的在线数要包含游客（漏斗第一段就是游客产生的）。用「近 5 分钟
 *     有事件」这个**行为口径**，游客也算得进来，且不依赖 Redis 是否健康。
 *   - `today_pv`      今日入库事件总数（`dwd_behavior_log`，已洗净）
 *   - `today_uv`      今日不同 `session_id` 数（与前端 `session_id` 口径一致）
 *   - `today_orders`  今日 `order_package` 事件数
 *   - `today_tickets` 今日 `submit_ticket` 事件数
 *
 * <p>四个 {@code today_*} 每 2 秒直接查库。数据量是演示级（今日几万行），
 * 走 {@code event_date} 分区裁剪后开销可以忽略；换来的是**重启后数字不丢、
 * 与 Flink 写入完全同源**，不再维护一份容易漂移的内存累计量。
 */
@Service
public class MetricService {

    private static final Logger log = LoggerFactory.getLogger(MetricService.class);

    private static final ZoneId CHINA = ZoneId.of("Asia/Shanghai");
    private static final ZoneOffset UTC = ZoneOffset.UTC;
    private static final DateTimeFormatter STAT_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("M/d");

    /** 五个指标编码。顺序就是快照里的顺序，与前端数字卡的顺序一致。 */
    public static final List<String> CODES = List.of(
            "online_users", "today_pv", "today_uv", "today_orders", "today_tickets");

    /** 在线判定窗口（分钟）：近 5 分钟有事件即视为在线。 */
    private static final int ONLINE_WINDOW_MINUTES = 5;

    /** 趋势图两种窗口的桶宽（分钟），与前端 `BUCKET_MINUTES` 一致。 */
    private static final int BUCKET_1H = 5;
    private static final int BUCKET_24H = 60;

    /** 热力图天数：近 7 天（含今天）。 */
    private static final int HEATMAP_DAYS = 7;

    private final JdbcTemplate jdbc;
    private final SseHub hub;

    /** 最近一次成功取到的快照；数据库短暂不可用时用它继续推（不把大屏清零）。 */
    private volatile List<MetricRecord> lastSnapshot;

    public MetricService(JdbcTemplate jdbc, SseHub hub) {
        this.jdbc = jdbc;
        this.hub = hub;
    }

    /* ------------------------------------------------------------------ *
     *  定时取数与推送                                                        *
     * ------------------------------------------------------------------ */

    /**
     * 每 2 秒：从库里取一遍五个指标，落 `dws_metric_realtime`，再推一条 SSE。
     *
     * 推的频率高于指标真正变化的频率是**有意的**：大屏要"活着"，而
     * `online_users` 每 2 秒都可能变。若只在有事件时推，用户少的时候
     * 大屏会长时间纹丝不动，看起来像断了。
     */
    @Scheduled(fixedRate = 2000)
    public void tick() {
        List<MetricRecord> values;
        try {
            values = snapshot();
        } catch (DataAccessException e) {
            // 首次启动就取不到数时没有旧值可推；有旧值则保持“活着”。
            log.warn("实时指标取数失败：{}", e.getMessage());
            return;
        }
        for (MetricRecord record : values) {
            persist(record);
            hub.push(record);
        }
    }

    private void persist(MetricRecord record) {
        try {
            // stat_time 截到分钟：同一分钟内 30 次推送写的是同一行（ON DUPLICATE
            // KEY UPDATE），dws 表不会按 2 秒一条迅速膨胀。
            LocalDateTime statTime = LocalDateTime.parse(record.stat_time(), STAT_TIME).withSecond(0);
            jdbc.update("INSERT INTO dws_metric_realtime (metric_code, metric_value, stat_time) "
                            + "VALUES (?, ?, ?) "
                            + "ON DUPLICATE KEY UPDATE metric_value = VALUES(metric_value)",
                    record.metric_code(), record.metric_value(), statTime);
        } catch (DataAccessException e) {
            // 落库失败不影响推送：大屏看的是实时值，dws 只是留痕。
            log.warn("实时指标落库失败：{}", e.getMessage());
        }
    }

    /* ------------------------------------------------------------------ *
     *  查询接口用的取数                                                     *
     * ------------------------------------------------------------------ */

    /**
     * 五个指标的当前值（板块①的初值，SSE 连上之前先拉一次）。
     *
     * <p>四个 {@code today_*} 直接从 {@code dwd_behavior_log} 数，不维护内存
     * 累计量：Flink 写入与这里读取共用同一张表，重启后数字不会“跳回去”。
     *
     * <p>数据库短暂不可用时返回上一次快照，让大屏继续显示旧值——清零比
     * 显示旧值更糟。第一次启动就取不到数时才把异常抛给调用方。
     */
    public List<MetricRecord> snapshot() {
        try {
            String statText = STAT_TIME.format(LocalDateTime.now(CHINA));
            LocalDate today = LocalDate.now(CHINA);
            List<MetricRecord> values = List.of(
                    new MetricRecord("online_users", onlineUsers(), statText),
                    new MetricRecord("today_pv", todayCount(today, null), statText),
                    new MetricRecord("today_uv", todayUv(today), statText),
                    new MetricRecord("today_orders", todayCount(today, "order_package"), statText),
                    new MetricRecord("today_tickets", todayCount(today, "submit_ticket"), statText));
            lastSnapshot = values;
            return values;
        } catch (DataAccessException e) {
            if (lastSnapshot != null) {
                log.warn("实时指标取数失败，沿用上一次快照：{}", e.getMessage());
                return lastSnapshot;
            }
            throw e;
        }
    }

    /** 今日事件总数；传 {@code eventCode} 时只数该事件。 */
    private long todayCount(LocalDate today, String eventCode) {
        if (eventCode == null) {
            return count("SELECT COUNT(*) FROM dwd_behavior_log WHERE event_date = ?",
                    Date.valueOf(today));
        }
        return count("SELECT COUNT(*) FROM dwd_behavior_log "
                        + "WHERE event_date = ? AND event_code = ?",
                Date.valueOf(today), eventCode);
    }

    /** 今日不同 session 数。 */
    private long todayUv(LocalDate today) {
        return count("SELECT COUNT(DISTINCT session_id) FROM dwd_behavior_log "
                + "WHERE event_date = ?", Date.valueOf(today));
    }

    /**
     * 访问趋势。`range` 取 `1h` / `24h`，其余按 `1h` 处理。
     *
     * ⚠️ 横轴**先定死**：桶的起点由当前时刻倒推出来，天然等距、个数固定。
     *    绝不能「查出来有哪些桶就画哪些」——某个 5 分钟没人访问，那一格会
     *    整格消失，折线直接连过去，图上完全看不出这里断过。
     */
    public List<TrendPoint> trend(String range) {
        boolean day = "24h".equals(range);
        int unit = day ? BUCKET_24H : BUCKET_1H;
        int buckets = day ? 24 : 12;

        LocalDateTime nowChina = LocalDateTime.now(CHINA).withSecond(0).withNano(0);
        LocalDateTime last = floor(nowChina, unit);
        LocalDateTime start = last.minusMinutes((long) unit * (buckets - 1));

        long[] counts = new long[buckets];
        collectEventTimes(start, last.plusMinutes(unit), china -> {
            long index = Duration.between(start, china).toMinutes() / unit;
            if (index >= 0 && index < buckets) {
                counts[(int) index]++;
            }
        });

        List<TrendPoint> points = new ArrayList<>(buckets);
        for (int i = 0; i < buckets; i++) {
            points.add(new TrendPoint(
                    STAT_TIME.format(start.plusMinutes((long) unit * i)), counts[i]));
        }
        return points;
    }

    /** 今日页面热度 Top N。排序与截断都在 SQL 里。 */
    public List<PageHeatRow> pageHeat(int limit) {
        int safe = Math.max(1, Math.min(limit, 50));
        return jdbc.query(
                "SELECT page, COUNT(*) AS c FROM dwd_behavior_log "
                        + "WHERE event_date = ? GROUP BY page ORDER BY c DESC, page ASC LIMIT ?",
                (rs, i) -> new PageHeatRow(rs.getString("page"), rs.getLong("c")),
                Date.valueOf(LocalDate.now(CHINA)), safe);
    }

    /** 今日 12 个事件的计数（板块②环形图；类别聚合在前端做）。 */
    public List<EventCount> eventCounts() {
        return jdbc.query(
                "SELECT event_code, COUNT(*) AS c FROM dwd_behavior_log "
                        + "WHERE event_date = ? GROUP BY event_code ORDER BY c DESC, event_code ASC",
                (rs, i) -> new EventCount(rs.getString("event_code"), rs.getLong("c")),
                Date.valueOf(LocalDate.now(CHINA)));
    }

    /**
     * 近 7 天 × 24 小时热力图。
     *
     * `event_time` 存的是 UTC，这里 +8 小时换成北京时段——热力图看的是
     * 「几点上网」，用 UTC 会把晚高峰画到下午，图看着正常但结论是错的。
     */
    public Heatmap hourlyHeatmap() {
        LocalDate today = LocalDate.now(CHINA);
        LocalDate firstDay = today.minusDays(HEATMAP_DAYS - 1);
        long[][] counts = new long[HEATMAP_DAYS][24];

        try {
            jdbc.query("SELECT event_time FROM dwd_behavior_log WHERE event_date BETWEEN ? AND ?",
                    (RowCallbackHandler) rs -> addToHourly(counts, firstDay, rs),
                    Date.valueOf(firstDay), Date.valueOf(today));
        } catch (DataAccessException e) {
            log.warn("读取热力图数据失败：{}", e.getMessage());
        }

        List<String> days = new ArrayList<>(HEATMAP_DAYS);
        for (int i = 0; i < HEATMAP_DAYS; i++) {
            days.add(DAY_LABEL.format(firstDay.plusDays(i)));
        }

        int nowHour = LocalDateTime.now(CHINA).getHour();
        List<List<Object>> series = new ArrayList<>(HEATMAP_DAYS * 24);
        for (int hour = 0; hour < 24; hour++) {
            for (int d = 0; d < HEATMAP_DAYS; d++) {
                boolean future = d == HEATMAP_DAYS - 1 && hour > nowHour;
                // 未来时段留 null（"还不知道"），已过去但无人访问的时段是 0。
                series.add(Arrays.asList(hour, d, future ? null : counts[d][hour]));
            }
        }
        return new Heatmap(days, series);
    }

    private void addToHourly(long[][] counts, LocalDate firstDay, ResultSet rs) throws SQLException {
        LocalDateTime utc = rs.getObject("event_time", LocalDateTime.class);
        if (utc == null) {
            return;
        }
        LocalDateTime china = utc.plusHours(8);
        int d = (int) ChronoUnit.DAYS.between(firstDay, china.toLocalDate());
        int h = china.getHour();
        if (d >= 0 && d < counts.length) {
            counts[d][h]++;
        }
    }

    /* ------------------------------------------------------------------ *
     *  内部                                                                 *
     * ------------------------------------------------------------------ */

    /**
     * 在线人数：近 5 分钟有事件的不同 session。
     *
     * ⚠️ 额外带一个 `event_date >= 昨天`：这句每 2 秒跑一次，只按 `event_time`
     *    过滤用不上分区列，会全表扫（现有 5.6 万行时就是每 2 秒一次全扫）。
     *    带上日期是为了让它落在当前月分区里。多算一天是安全方向——窗口只有
     *    5 分钟，跨零点的边界情况正好被这一天的余量兜住。
     */
    private long onlineUsers() {
        LocalDate today = LocalDate.now(CHINA);
        Long n = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT session_id) FROM dwd_behavior_log "
                        + "WHERE event_date >= ? AND event_time >= ?",
                Long.class,
                Date.valueOf(today.minusDays(1)),
                LocalDateTime.now(UTC).minusMinutes(ONLINE_WINDOW_MINUTES));
        return n == null ? 0 : n;
    }

    /**
     * 取 [startChina, endChina) 内的所有 `event_time`（UTC 存储，+8 后回调）。
     *
     * 为什么把"逐行转换"抽出来：趋势与热力图都要做同一件事，而时区换算
     * 只要抄错一次（+8 写成 -8），图能画出来、数据却整体错位，
     * 且不会报错。集中在一处，至少只有一处要看。
     */
    private void collectEventTimes(LocalDateTime startChina, LocalDateTime endChina,
                                   java.util.function.Consumer<LocalDateTime> sink) {
        try {
            // 带上 `event_date` 是为了走分区裁剪（`event_time` 上没有索引）。
            jdbc.query("SELECT event_time FROM dwd_behavior_log "
                            + "WHERE event_date >= ? AND event_time >= ? AND event_time < ?",
                    (RowCallbackHandler) rs -> {
                        LocalDateTime utc = rs.getObject("event_time", LocalDateTime.class);
                        if (utc != null) {
                            sink.accept(utc.plusHours(8));
                        }
                    },
                    Date.valueOf(startChina.toLocalDate()),
                    startChina.minusHours(8), endChina.minusHours(8));
        } catch (DataAccessException e) {
            log.warn("读取趋势数据失败：{}", e.getMessage());
        }
    }

    /** 向下取整到 `unit` 分钟（`unit=60` 时即"整点"）。 */
    private static LocalDateTime floor(LocalDateTime time, int unit) {
        int minute = time.getMinute() - time.getMinute() % unit;
        return time.withMinute(minute).withSecond(0).withNano(0);
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }
}
