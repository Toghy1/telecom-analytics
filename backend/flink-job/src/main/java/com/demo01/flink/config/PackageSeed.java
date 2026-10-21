package com.demo01.flink.config;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 套餐种子，与 {@code etl/etl.py} 的 {@code PACKAGE_SEED} 逐字一致。
 *
 * <p>为什么作业里还要带一份：套餐分布要 join 套餐名，而 {@code users.json}
 * 只给 {@code package_id}；{@code --seed} 时也要用它重灌 {@code t_package}。
 * 种子是演示数据的一部分，不随运行变化，放在代码里比读库少一个依赖。
 */
public final class PackageSeed {

    public static final class Def {

        private final long id;
        private final String name;
        private final BigDecimal monthlyFee;
        private final BigDecimal dataQuota;
        private final int voiceQuota;
        private final int smsQuota;
        private final String description;
        private final int status;
        private final String createTime;

        public Def(long id, String name, String monthlyFee, String dataQuota,
                   int voiceQuota, int smsQuota, String description,
                   int status, String createTime) {
            this.id = id;
            this.name = name;
            this.monthlyFee = new BigDecimal(monthlyFee);
            this.dataQuota = new BigDecimal(dataQuota);
            this.voiceQuota = voiceQuota;
            this.smsQuota = smsQuota;
            this.description = description;
            this.status = status;
            this.createTime = createTime;
        }

        public long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public BigDecimal getMonthlyFee() {
            return monthlyFee;
        }

        public BigDecimal getDataQuota() {
            return dataQuota;
        }

        public int getVoiceQuota() {
            return voiceQuota;
        }

        public int getSmsQuota() {
            return smsQuota;
        }

        public String getDescription() {
            return description;
        }

        public int getStatus() {
            return status;
        }

        public String getCreateTime() {
            return createTime;
        }
    }

    private static final List<Def> ALL = Collections.unmodifiableList(Arrays.asList(
            new Def(1, "畅享 5G 39 元套餐", "39.00", "30.00", 300, 100,
                    "30GB 国内流量，300 分钟国内通话，超出后 0.29 元/分钟。",
                    1, "2024-01-05 09:00:00"),
            new Def(2, "畅享 5G 79 元套餐", "79.00", "60.00", 600, 200,
                    "60GB 国内流量，600 分钟国内通话，赠送 5G 极速服务。",
                    1, "2024-01-05 09:00:00"),
            new Def(3, "畅享 5G 129 元套餐", "129.00", "100.00", 1000, 300,
                    "100GB 国内流量，1000 分钟国内通话，附赠 1 张副卡。",
                    1, "2024-01-05 09:00:00"),
            new Def(4, "孝心卡 19 元套餐", "19.00", "5.00", 200, 50,
                    "面向老年用户，5GB 流量 + 200 分钟通话，支持设置亲情号码。",
                    1, "2026-02-11 14:30:00"),
            new Def(5, "学生特惠 29 元套餐", "29.00", "40.00", 100, 100,
                    "校园专项，40GB 定向流量，需通过学生身份认证。",
                    0, "2026-03-02 10:00:00")));

    public static List<Def> all() {
        return ALL;
    }

    /** id → 套餐名。**包含已停售套餐**：存量用户仍可能持有它。 */
    public static Map<Long, String> names() {
        Map<Long, String> map = new LinkedHashMap<Long, String>();
        for (Def d : ALL) {
            map.put(d.getId(), d.getName());
        }
        return map;
    }

    private PackageSeed() {
    }
}
