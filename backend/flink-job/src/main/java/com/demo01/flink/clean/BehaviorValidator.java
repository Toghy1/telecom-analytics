package com.demo01.flink.clean;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 七条判脏规则（《开发文档》§5.3 / 决策「脏数据规则与双通道注入」）。
 *
 * <p>⚠️ **这是 {@code etl/etl.py} 同名逻辑的 Java 移植，不是重新设计的。**
 * 顺序、边界、正则必须逐字对齐；唯一有意的差异是规则⑥的判据：
 * {@code etl.py} 用内存 {@code seen} 集合，本类把「查重 + 记账」抽成
 * {@link DuplicateChecker}，由调用方决定用内存集合（离线）还是
 * 「有界内存 + 查库」（实时）。七条规则本身只有这一份实现。
 *
 * <p>规则顺序（**不可调换**）：
 * <pre>
 *   ① MISSING_REQUIRED   必填字段缺失
 *   ② ENUM_OUT_OF_RANGE  event_code / device 越界
 *   ③ TYPE_ERROR         user_id 非整数、event_time 不是 ISO8601
 *   ④ TIME_ILLEGAL       格式对但落在未来 / 早于上线日
 *   ⑤ ID_FORMAT_ERROR    event_id / session_id / ip 格式非法
 *   ⑥ DUPLICATE          (event_id, event_date) 重复
 *   ⑦ OVERLONG_GARBLED   page 超 64 字符或含控制字符 / U+FFFD
 * </pre>
 *
 * <p>顺序为什么不能换：一行可能同时犯好几条，先判哪条决定了它进死信时
 * 记的是哪个 {@code fail_reason}。例如 event_time 缺失且 page 超长——
 * 先判①记 MISSING_REQUIRED，先判⑦记 OVERLONG_GARBLED，两种都「说得通」，
 * 于是批量通道与实时通道各说各的，分布图立刻对不上。
 *
 * <p>⚠️ 规则⑥的 key 在规则⑦**之前**加入 {@code seen}，与 {@code etl.py}
 * 一致：一行即使因 page 超长判脏，它占用的 (event_id, event_date) 仍然生效，
 * 之后同 key 的行记 DUPLICATE 而不是再记一次超长。
 */
public final class BehaviorValidator {

    /** 12 个埋点事件（§5.1.1），与前端 {@code EVENT_CATEGORY} 的键逐字一致。 */
    public static final Set<String> EVENT_CODES = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList(
                    "view_home", "view_package_list", "view_promo",
                    "login", "logout",
                    "view_dashboard", "query_balance", "query_bill", "query_usage",
                    "order_package", "submit_ticket", "view_profile")));

    /** 规则①的必填字段（§5.1.2）。user_id / ip / ext 可空，不在此列。 */
    private static final String[] REQUIRED_FIELDS = {
            "event_id", "event_code", "event_time", "session_id", "page", "region", "device"
    };

    private static final Set<String> DEVICES = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList("PC", "MOBILE", "OTHER")));

    /** 系统上线日：早于它的时间视为「越界」（规则④）。 */
    private static final Instant SYSTEM_ONLINE_DATE = Instant.parse("2024-01-01T00:00:00Z");

    /** 北京时区。event_date 按它换算，**不能直接取 UTC 字面量的日期**。 */
    private static final ZoneId CHINA = ZoneId.of("Asia/Shanghai");

    /** 与前端 {@code toISOString()} 同形：UTC + 毫秒。 */
    private static final Pattern TIME_RE =
            Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z$");

    /**
     * 宽松 UUID：36 位、8-4-4-4-12、十六进制。
     * **刻意不校验 RFC 版本位 / 变体位**——造数脚本用随机整数，
     * 严格式会把全部正常行判成脏（决策「正则只做格式校验」）。
     */
    private static final Pattern UUID_RE = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-"
                    + "[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private static final Pattern IPV4_RE =
            Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");

    private BehaviorValidator() {
    }

    /**
     * 判定一个事件。
     *
     * @param row  已解析的 JSON 对象
     * @param now  判定时刻（规则④的上界）
     * @param seen 已通过前 5 条规则的 {@code event_id + \0 + event_date} 集合；
     *             本方法会在规则⑥处读写它。调用方必须按输入顺序串行调用
     *             （Flink 作业里是单并行度校验算子），否则「谁是第一条」不确定
     * @return 脏行返回带 {@code failReason} 的结果；干净行 {@code failReason == null}，
     *         并带上北京日期与 UTC 时间
     */
    public static Verdict validate(JsonNode row, Instant now, Set<String> seen) {
        return validate(row, now, new SetDuplicateChecker(seen));
    }

    /**
     * 判定一个事件，重复判据由调用方提供。
     *
     * <p>离线作业用内存 {@code Set}（{@link #validate(JsonNode, Instant, Set)}）；
     * 实时作业用「有界内存 + 查库」的实现（{@code DbDuplicateChecker}）。
     * 七条规则的顺序、边界完全共用，实时与离线不会各写一份。
     *
     * <p>⚠️ {@link DuplicateChecker#remember} 必须在规则⑦之前调用，理由与
     * {@code etl.py} 一致：一行即使因 page 超长判脏，它占用的
     * {@code (event_id, event_date)} 仍然生效。
     */
    public static Verdict validate(JsonNode row, Instant now, DuplicateChecker duplicate) {
        // JSON 本身不是对象（数组 / 标量）不在七条规则里。etl.py 会在
        // row.get() 处直接抛异常中止整批；这里按类型错误收进死信，至少不丢行。
        if (row == null || !row.isObject()) {
            return fail("TYPE_ERROR");
        }

        // ① 必填字段缺失
        for (String field : REQUIRED_FIELDS) {
            JsonNode v = row.get(field);
            if (v == null || v.isNull()) {
                return fail("MISSING_REQUIRED");
            }
            if (v.isTextual() && v.textValue().isEmpty()) {
                return fail("MISSING_REQUIRED");
            }
        }

        // ② 枚举越界（非字符串也走这里：asText 得到的值必然不在集合里）
        if (!EVENT_CODES.contains(text(row.get("event_code")))) {
            return fail("ENUM_OUT_OF_RANGE");
        }
        if (!DEVICES.contains(text(row.get("device")))) {
            return fail("ENUM_OUT_OF_RANGE");
        }

        // ③ 字段类型错误
        JsonNode uid = row.get("user_id");
        if (uid != null && !uid.isNull() && !uid.isIntegralNumber()) {
            return fail("TYPE_ERROR");
        }
        String timeText = text(row.get("event_time"));
        LocalDateTime timeUtc = parseUtc(timeText);
        if (timeUtc == null) {
            return fail("TYPE_ERROR");
        }

        // ④ 时间戳越界（未来 / 早于上线日）
        Instant time = timeUtc.toInstant(ZoneOffset.UTC);
        if (time.isAfter(now) || time.isBefore(SYSTEM_ONLINE_DATE)) {
            return fail("TIME_ILLEGAL");
        }
        LocalDate eventDate = time.atZone(CHINA).toLocalDate();

        // ⑤ 标识格式非法
        if (!isUuid(row.get("event_id")) || !isUuid(row.get("session_id"))) {
            return fail("ID_FORMAT_ERROR");
        }
        JsonNode ip = row.get("ip");
        if (ip != null && !ip.isNull() && !isIpv4(ip)) {
            return fail("ID_FORMAT_ERROR");
        }

        // ⑥ 重复上报（与 uk_event (event_id, event_date) 同口径）。
        //    ⚠️ 必须在规则⑦之前 remember，理由见类注释。
        String eventId = row.get("event_id").textValue();
        if (duplicate.isDuplicate(eventId, eventDate)) {
            return fail("DUPLICATE");
        }
        duplicate.remember(eventId, eventDate);

        // ⑦ 字段超长 / 乱码
        if (pageBad(row.get("page"))) {
            // 仍把 eventDate 带回去：后续若要在死信里补分区信息，不必重算。
            return new Verdict("OVERLONG_GARBLED", eventDate, timeUtc);
        }

        return new Verdict(null, eventDate, timeUtc);
    }

    /* ------------------------------------------------------------------ *
     *  规则③④：时间                                                         *
     * ------------------------------------------------------------------ */

    /**
     * ISO8601（UTC，毫秒）→ UTC {@code LocalDateTime}；格式不对返回 null。
     *
     * <p>正则已保证形状，这里切掉末尾的 {@code Z} 交给标准 ISO 解析器——
     * 自己写 SimpleDateFormat 会在 yyyy 与 YYYY、宽松解析上踩坑，而那两个坑
     * 都表现为「个别时间判错、其余全对」，最难查。
     */
    private static LocalDateTime parseUtc(String text) {
        if (text == null || !TIME_RE.matcher(text).matches()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.substring(0, 23));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /* ------------------------------------------------------------------ *
     *  规则⑦：page                                                          *
     * ------------------------------------------------------------------ */

    private static boolean pageBad(JsonNode page) {
        if (page == null || !page.isTextual()) {
            return true;
        }
        String value = page.textValue();
        // ⚠️ Java 的 length() 数的是 UTF-16 码元，Python 的 len() 数的是码点：
        //    一个 emoji 在这里算 2、在 etl.py 里算 1。page 是路径，正常数据
        //    不会出现，真要出现也是乱码——正好该判脏，所以不为此改口径。
        if (value.length() > 64) {
            return true;
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch < 32 || ch == 127 || ch == '�') {
                return true;
            }
        }
        return false;
    }

    /* ------------------------------------------------------------------ *
     *  小工具                                                               *
     * ------------------------------------------------------------------ */

    /** JSON 节点 → 字符串；缺失或 null 返回 null。 */
    private static String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private static boolean isUuid(JsonNode node) {
        return node != null && node.isTextual() && UUID_RE.matcher(node.textValue()).matches();
    }

    private static boolean isIpv4(JsonNode node) {
        if (!node.isTextual()) {
            return false;
        }
        Matcher m = IPV4_RE.matcher(node.textValue());
        if (!m.matches()) {
            return false;
        }
        for (int i = 1; i <= 4; i++) {
            int part = Integer.parseInt(m.group(i));
            if (part < 0 || part > 255) {
                return false;
            }
        }
        return true;
    }

    private static Verdict fail(String reason) {
        return new Verdict(reason, null, null);
    }

    /**
     * 规则⑥的判据与记账。
     *
     * <p>为什么把「查重」和「记账」拆成两个方法：校验器要先问「见过吗」，
     * 只有没见过的才继续走规则⑦，然后**无论规则⑦是否通过**都要记账。
     * 一个方法里做不了这个顺序。
     */
    public interface DuplicateChecker {

        /** 该 {@code (event_id, event_date)} 是否已经出现过。 */
        boolean isDuplicate(String eventId, LocalDate eventDate);

        /** 记下这个 key；规则⑥通过后立即调用，早于规则⑦。 */
        void remember(String eventId, LocalDate eventDate);
    }

    /** 离线作业的判据：内存集合，按输入顺序串行调用。 */
    private static final class SetDuplicateChecker implements DuplicateChecker {

        private final Set<String> seen;

        private SetDuplicateChecker(Set<String> seen) {
            this.seen = seen;
        }

        @Override
        public boolean isDuplicate(String eventId, LocalDate eventDate) {
            return seen.contains(key(eventId, eventDate));
        }

        @Override
        public void remember(String eventId, LocalDate eventDate) {
            seen.add(key(eventId, eventDate));
        }

        private static String key(String eventId, LocalDate eventDate) {
            return eventId + '\u0000' + eventDate;
        }
    }

    /**
     * 判定结果。**Java 8 工程，不能用 record**。
     */
    public static final class Verdict implements Serializable {

        private static final long serialVersionUID = 1L;

        private final String failReason;
        private final LocalDate eventDate;
        private final LocalDateTime eventTimeUtc;

        public Verdict(String failReason, LocalDate eventDate, LocalDateTime eventTimeUtc) {
            this.failReason = failReason;
            this.eventDate = eventDate;
            this.eventTimeUtc = eventTimeUtc;
        }

        public String getFailReason() {
            return failReason;
        }

        public LocalDate getEventDate() {
            return eventDate;
        }

        public LocalDateTime getEventTimeUtc() {
            return eventTimeUtc;
        }

        public boolean dirty() {
            return failReason != null;
        }
    }
}
