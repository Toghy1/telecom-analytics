package com.demo01.flink.io;

import com.demo01.flink.config.JdbcConfig;
import com.demo01.flink.config.JobConfig;
import com.demo01.flink.config.PackageSeed;
import com.demo01.flink.model.User;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;

/**
 * 作业启动前的清理与种子灌入。
 *
 * <p>与 {@code etl/etl.py} 的 {@code reset_tables} / {@code seed_business}
 * 一一对应。作业本身**不**在 Flink 算子里面做这些事——清表和灌种子是
 * 「一次性的准备动作」，放进数据流会让重跑语义变得难解释。
 */
public final class JdbcSupport {

    /** 演示账号统一口令的 BCrypt 哈希（明文 123456），与 etl.py 逐字一致。 */
    private static final String DEMO_PASSWORD_HASH =
            "$2b$10$9ch6/n2uPdZz9kjuXdCtAOxa/gq8uE7ExOrIcw7IPiqN94Y2y9Ngu";

    public static Connection connect(JdbcConfig config) throws Exception {
        // Java 8 + MySQL Connector/J 8.x：显式加载驱动，避免本地运行时的 SPI 差异。
        Class.forName("com.mysql.cj.jdbc.Driver");
        return DriverManager.getConnection(config.jdbcUrl(), config.getUser(), config.getPassword());
    }

    public static void reset(JobConfig config) throws Exception {
        Connection conn = connect(config);
        try {
            Statement st = conn.createStatement();
            try {
                st.execute("TRUNCATE TABLE dwd_behavior_log");
                st.execute("TRUNCATE TABLE dwd_behavior_dirty");
                st.execute("TRUNCATE TABLE dws_metric_offline");
            } finally {
                st.close();
            }
        } finally {
            conn.close();
        }
    }

    public static void seed(JobConfig config, List<User> users) throws Exception {
        Connection conn = connect(config);
        try {
            conn.setAutoCommit(false);

            Statement st = conn.createStatement();
            try {
                // 与 etl.py 同序：先账号后套餐。
                st.execute("DELETE FROM t_account");
                st.execute("DELETE FROM t_package");
            } finally {
                st.close();
            }

            PreparedStatement pkg = conn.prepareStatement(
                    "INSERT INTO t_package (id, name, monthly_fee, data_quota, voice_quota, "
                            + "sms_quota, description, status, create_time) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
            try {
                for (PackageSeed.Def d : PackageSeed.all()) {
                    pkg.setLong(1, d.getId());
                    pkg.setString(2, d.getName());
                    pkg.setBigDecimal(3, d.getMonthlyFee());
                    pkg.setBigDecimal(4, d.getDataQuota());
                    pkg.setInt(5, d.getVoiceQuota());
                    pkg.setInt(6, d.getSmsQuota());
                    pkg.setString(7, d.getDescription());
                    pkg.setInt(8, d.getStatus());
                    // 按字面量写：集群 JVM 可能是 UTC，用 Timestamp.valueOf
                    // 会被 serverTimezone 再换一次，create_time 整体偏移 8 小时。
                    pkg.setObject(9, d.getCreateTime());
                    pkg.addBatch();
                }
                pkg.executeBatch();
            } finally {
                pkg.close();
            }

            PreparedStatement acc = conn.prepareStatement(
                    "INSERT INTO t_account (id, login_name, password, role, id_card, region, "
                            + "package_id, balance, status, create_time) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            try {
                int n = 0;
                for (User u : users) {
                    acc.setLong(1, u.getUserId());
                    acc.setString(2, u.getLoginName());
                    acc.setString(3, DEMO_PASSWORD_HASH);
                    acc.setString(4, "USER");
                    acc.setNull(5, Types.VARCHAR);      // id_card 本阶段不造
                    acc.setString(6, u.getRegion());
                    acc.setLong(7, u.getPackageId());
                    acc.setNull(8, Types.DECIMAL);      // balance 本阶段不造
                    acc.setInt(9, 1);
                    acc.setObject(10, u.getRegisterDate().atStartOfDay());
                    acc.addBatch();
                    if (++n % 1000 == 0) {
                        acc.executeBatch();
                    }
                }
                acc.executeBatch();
            } finally {
                acc.close();
            }

            conn.commit();
        } catch (Exception e) {
            conn.rollback();
            throw e;
        } finally {
            conn.close();
        }
    }

    private JdbcSupport() {
    }
}
