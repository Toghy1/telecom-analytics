package com.demo01.flink.metric;

import com.demo01.flink.model.Event;
import com.demo01.flink.model.MetricRow;
import com.demo01.flink.model.User;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 离线指标聚合：收到结束标记后一次性吐出全部指标行。
 *
 * <p>口径逐条对齐 {@code etl/etl.py#compute_metrics}：
 * <ul>
 *   <li>地域 / 套餐分布：分母是 {@code users.json} 全量名册；套餐**包含已停售**；</li>
 *   <li>活跃度：近 30 天干净事件数，≥50 高频 / ≥10 中频 / 其余低频，零事件也算低频；</li>
 *   <li>漏斗：**会话级去重**——一个会话内出现过某事件就记一次，不是事件条数；</li>
 *   <li>留存：cohort 锚点 {@code register_date}，只落观察期已满的 cohort；
 *       算不出来的不落行，前端按日历补 null（决策「离线指标横轴来自日历」）。</li>
 * </ul>
 *
 * <p>⚠️ 单并行度。漏斗、留存、三个用户维度分布都是**全局**聚合，按 key 分区
 * 只会得到每个分区各一份局部结果；要让它们相加得出全局值，得再做一轮
 * 汇总，代码量和出错面都变大。当前数据量（5.6 万干净行、500 用户）单并行度
 * 毫秒级跑完，没必要提前分布式化。
 *
 * <p>指标只在这里算一次：实时链路算的是另一组指标，两条链路不重复计算同一项。
 */
public class OfflineMetricAggregator extends ProcessFunction<Event, MetricRow> {

    private static final long serialVersionUID = 1L;

    private static final String[] ACTIVITY_LABELS = {"高频活跃", "中频活跃", "低频活跃"};
    private static final int ACTIVITY_HIGH = 50;
    private static final int ACTIVITY_MID = 10;

    private static final int MASK_HOME = 1;
    private static final int MASK_LOGIN = 2;
    private static final int MASK_QUERY = 4;
    private static final int MASK_ORDER = 8;

    private final List<User> users;
    private final Map<Long, String> packageNames;
    private final LocalDate statDate;

    /** session_id → 漏斗四个阶段的位掩码。 */
    private transient Map<String, Integer> sessionMasks;

    /** user_id → 近 30 天干净事件数。 */
    private transient Map<Long, Integer> userCounts;

    /** 北京日期 → 当天有干净事件的用户集合，用于留存。 */
    private transient Map<LocalDate, Set<Long>> activeByDay;

    /** 活跃度统计窗口的下界：stat_date - 29 天。 */
    private transient LocalDate since;

    public OfflineMetricAggregator(List<User> users, Map<Long, String> packageNames,
                                   LocalDate statDate) {
        this.users = users;
        this.packageNames = packageNames;
        this.statDate = statDate;
    }

    @Override
    public void open(Configuration parameters) {
        sessionMasks = new HashMap<String, Integer>();
        userCounts = new HashMap<Long, Integer>();
        activeByDay = new HashMap<LocalDate, Set<Long>>();
        since = statDate.minusDays(29);
    }

    @Override
    public void processElement(Event event, Context ctx, Collector<MetricRow> out) {
        if (event.isEnd()) {
            emit(out);
            return;
        }

        // 漏斗：同一个会话里，四个阶段各最多记一次。
        int bit = maskOf(event.getEventCode());
        if (bit != 0) {
            Integer old = sessionMasks.get(event.getSessionId());
            sessionMasks.put(event.getSessionId(), (old == null ? 0 : old) | bit);
        }

        Long userId = event.getUserId();
        if (userId != null) {
            LocalDate day = event.getEventDate();

            Set<Long> active = activeByDay.get(day);
            if (active == null) {
                active = new HashSet<Long>();
                activeByDay.put(day, active);
            }
            active.add(userId);

            // 活跃度只看窗口内的干净事件；留存要全量日期，所以上面不设窗口。
            if (!day.isBefore(since)) {
                Integer count = userCounts.get(userId);
                userCounts.put(userId, count == null ? 1 : count + 1);
            }
        }
    }

    private static int maskOf(String code) {
        if ("view_home".equals(code)) {
            return MASK_HOME;
        }
        if ("login".equals(code)) {
            return MASK_LOGIN;
        }
        if ("query_balance".equals(code) || "query_bill".equals(code)
                || "query_usage".equals(code)) {
            return MASK_QUERY;
        }
        if ("order_package".equals(code)) {
            return MASK_ORDER;
        }
        return 0;
    }

    private void emit(Collector<MetricRow> out) {
        emitRegionDist(out);
        emitPackageDist(out);
        emitActivityLevels(out);
        emitFunnel(out);
        emitRetention(out);
    }

    /** 地域分布：用户名册口径，逐字使用 users.json 的省份名。 */
    private void emitRegionDist(Collector<MetricRow> out) {
        Map<String, Long> counts = new LinkedHashMap<String, Long>();
        for (User u : users) {
            Long c = counts.get(u.getRegion());
            counts.put(u.getRegion(), c == null ? 1L : c + 1);
        }
        for (Map.Entry<String, Long> e : sortedByCountDesc(counts)) {
            out.collect(row("region_dist", "region", e.getKey(),
                    BigDecimal.valueOf(e.getValue())));
        }
    }

    /** 套餐分布：按用户当前套餐；不 join status = 1，已停售套餐也要出现。 */
    private void emitPackageDist(Collector<MetricRow> out) {
        Map<String, Long> counts = new LinkedHashMap<String, Long>();
        for (User u : users) {
            String name = packageNames.get(u.getPackageId());
            if (name == null) {
                throw new IllegalStateException("套餐不存在：" + u.getPackageId());
            }
            Long c = counts.get(name);
            counts.put(name, c == null ? 1L : c + 1);
        }
        for (Map.Entry<String, Long> e : sortedByCountDesc(counts)) {
            out.collect(row("package_dist", "package", e.getKey(),
                    BigDecimal.valueOf(e.getValue())));
        }
    }

    /** 活跃度分层：三档都落行，哪怕某一档是 0（前端按固定顺序取）。 */
    private void emitActivityLevels(Collector<MetricRow> out) {
        Map<String, Long> levelCounts = new LinkedHashMap<String, Long>();
        for (String label : ACTIVITY_LABELS) {
            levelCounts.put(label, 0L);
        }
        for (User u : users) {
            Integer n = userCounts.get(u.getUserId());
            int count = n == null ? 0 : n;
            String label;
            if (count >= ACTIVITY_HIGH) {
                label = ACTIVITY_LABELS[0];
            } else if (count >= ACTIVITY_MID) {
                label = ACTIVITY_LABELS[1];
            } else {
                label = ACTIVITY_LABELS[2];
            }
            levelCounts.put(label, levelCounts.get(label) + 1);
        }
        for (String label : ACTIVITY_LABELS) {
            out.collect(row("activity_level", "level", label,
                    BigDecimal.valueOf(levelCounts.get(label))));
        }
    }

    /** 漏斗：会话级去重，四个阶段独立计数（不要求严格按顺序发生）。 */
    private void emitFunnel(Collector<MetricRow> out) {
        long home = 0;
        long login = 0;
        long query = 0;
        long order = 0;
        for (Integer mask : sessionMasks.values()) {
            if ((mask & MASK_HOME) != 0) {
                home++;
            }
            if ((mask & MASK_LOGIN) != 0) {
                login++;
            }
            if ((mask & MASK_QUERY) != 0) {
                query++;
            }
            if ((mask & MASK_ORDER) != 0) {
                order++;
            }
        }
        out.collect(row("funnel", "step", "view_home", BigDecimal.valueOf(home)));
        out.collect(row("funnel", "step", "login", BigDecimal.valueOf(login)));
        out.collect(row("funnel", "step", "query", BigDecimal.valueOf(query)));
        out.collect(row("funnel", "step", "order_package", BigDecimal.valueOf(order)));
    }

    /**
     * 留存：cohort 是注册日，回访日是「当天有任意干净事件的用户集合」。
     *
     * <p>只落观察期已满的行：{@code target > stat_date} 时 continue，
     * 与 etl.py 一致。前端按日历补 null，绝不补 0。
     */
    private void emitRetention(Collector<MetricRow> out) {
        Map<LocalDate, Set<Long>> cohort = new HashMap<LocalDate, Set<Long>>();
        for (User u : users) {
            Set<Long> ids = cohort.get(u.getRegisterDate());
            if (ids == null) {
                ids = new HashSet<Long>();
                cohort.put(u.getRegisterDate(), ids);
            }
            ids.add(u.getUserId());
        }

        String[][] kinds = {{"d1", "1"}, {"d7", "7"}};
        for (int offset = 13; offset >= 0; offset--) {
            LocalDate start = statDate.minusDays(offset);
            Set<Long> base = cohort.get(start);
            if (base == null || base.isEmpty()) {
                continue;
            }
            for (String[] kind : kinds) {
                LocalDate target = start.plusDays(Integer.parseInt(kind[1]));
                if (target.isAfter(statDate)) {
                    continue;               // 观察期未满，不落行
                }
                Set<Long> active = activeByDay.get(target);
                int retained = 0;
                if (active != null) {
                    for (Long id : base) {
                        if (active.contains(id)) {
                            retained++;
                        }
                    }
                }
                // Python 的 round 是 round-half-even，这里保持一致。
                BigDecimal rate = BigDecimal.valueOf(retained * 100.0 / base.size())
                        .setScale(1, RoundingMode.HALF_EVEN);
                out.collect(row("retention", "day", start + "|" + kind[0], rate));
            }
        }
    }

    private MetricRow row(String code, String dimension, String value, BigDecimal metric) {
        return new MetricRow(code, dimension, value, statDate, metric);
    }

    /** 按计数降序；计数相同保持插入顺序（对应 Python 稳定排序的行为）。 */
    private static List<Map.Entry<String, Long>> sortedByCountDesc(Map<String, Long> map) {
        List<Map.Entry<String, Long>> list =
                new ArrayList<Map.Entry<String, Long>>(map.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<String, Long>>() {
            @Override
            public int compare(Map.Entry<String, Long> a, Map.Entry<String, Long> b) {
                return Long.compare(b.getValue(), a.getValue());
            }
        });
        return list;
    }
}
