package com.demo01.flink.clean;

import com.demo01.flink.config.RealtimeConfig;
import com.demo01.flink.model.DirtyRecord;
import com.demo01.flink.model.Event;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 实时校验：Kafka 消息（JSON 文本）→ 干净事件 / 脏行侧输出。
 *
 * <p>七条规则复用 {@link BehaviorValidator}，与离线作业是**同一份实现**；
 * 唯一不同的是规则⑥的判据换成了 {@link DbDuplicateChecker}（有界内存 + 查库）。
 *
 * <p>与离线作业的两处语义差异，都是实时链路本身决定的：
 * <ul>
 *   <li>规则④的「未来」上界取**每条消息到达时的 now**，不是作业启动时刻。
 *       离线作业读的是历史文件，用启动时刻没问题；实时消息是持续来的，
 *       用启动时刻会把启动之后产生的消息全判成未来。</li>
 *   <li>脏行的 {@code fail_time} 取**当下北京时间**。离线作业用
 *       「日文件 00:00 + 5 分钟」是批次时间，实时没有批次。</li>
 * </ul>
 *
 * <p>⚠️ 单并行度：规则⑥的「第一条」需要顺序，{@link DbDuplicateChecker}
 * 也只有一个连接。
 */
public class RealtimeValidateFunction extends ProcessFunction<String, Event> {

    private static final long serialVersionUID = 1L;

    public static final OutputTag<DirtyRecord> DIRTY =
            new OutputTag<DirtyRecord>("realtime-dirty") {
            };

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ZoneId CHINA = ZoneId.of("Asia/Shanghai");

    private final RealtimeConfig config;

    private transient DbDuplicateChecker duplicate;

    public RealtimeValidateFunction(RealtimeConfig config) {
        this.config = config;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        duplicate = new DbDuplicateChecker(config);
    }

    @Override
    public void close() throws Exception {
        if (duplicate != null) {
            duplicate.close();
        }
    }

    @Override
    public void processElement(String line, Context ctx, Collector<Event> out) throws Exception {
        if (line == null || line.trim().isEmpty()) {
            ctx.output(DIRTY, dirty("TYPE_ERROR", line));
            return;
        }

        JsonNode node;
        try {
            node = MAPPER.readTree(line);
        } catch (Exception e) {
            // 业务接口已挡过「不是 JSON」；走到这里说明有人绕过接口直接灌 topic。
            ctx.output(DIRTY, dirty("TYPE_ERROR", line));
            return;
        }

        // 每条消息都取当前时刻：上界不能是作业启动时刻，否则启动后的新消息全判未来。
        BehaviorValidator.Verdict verdict =
                BehaviorValidator.validate(node, Instant.now(), duplicate);
        if (verdict.dirty()) {
            ctx.output(DIRTY, dirty(verdict.getFailReason(), line));
            return;
        }

        Event event = new Event();
        event.setEventId(text(node, "event_id"));
        event.setEventDate(verdict.getEventDate());
        event.setEventCode(text(node, "event_code"));
        event.setEventTime(verdict.getEventTimeUtc());

        JsonNode uid = node.get("user_id");
        event.setUserId(uid == null || uid.isNull() ? null : Long.valueOf(uid.longValue()));

        event.setSessionId(text(node, "session_id"));
        event.setPage(text(node, "page"));
        event.setRegion(text(node, "region"));
        event.setDevice(text(node, "device"));

        JsonNode ip = node.get("ip");
        event.setIp(ip == null || ip.isNull() ? null : ip.asText());

        JsonNode ext = node.get("ext");
        // 与离线作业一致：Jackson 序列化不带 Python json.dumps 的空格；
        // JSON 列语义等价，指标不读 ext。
        event.setExt(ext == null || ext.isNull() ? null : MAPPER.writeValueAsString(ext));

        out.collect(event);
    }

    private static DirtyRecord dirty(String reason, String raw) {
        return new DirtyRecord(reason, raw, LocalDateTime.now(CHINA));
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
