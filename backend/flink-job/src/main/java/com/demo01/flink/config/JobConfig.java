package com.demo01.flink.config;

import java.io.Serializable;

/**
 * 作业参数。默认值与 {@code etl/etl.py} 保持一致，方便两边对拍。
 *
 * <pre>
 *   --input   mock/out          造数产物目录
 *   --host    127.0.0.1         MySQL
 *   --port    3306
 *   --user    root
 *   --password                  默认取环境变量 DEMO01_DB_PASSWORD，再退回 123456
 *   --database demo01
 *   --reset                     跑之前 TRUNCATE 行为三表（与 etl.py 一致）
 *   --seed                      重灌 t_account / t_package 种子（与 etl.py 一致）
 * </pre>
 *
 * <p>⚠️ {@code --reset} 会清空 {@code dwd_behavior_log} / {@code dwd_behavior_dirty}
 * / {@code dws_metric_offline}，包括实时链路刚写进去的行。只对本地演示库用。
 */
public class JobConfig implements JdbcConfig {

    private static final long serialVersionUID = 1L;

    private String input = "mock/out";
    private String host = "127.0.0.1";
    private int port = 3306;
    private String user = "root";
    private String password = defaultPassword();
    private String database = "demo01";
    private boolean reset;
    private boolean seed;

    public static JobConfig parse(String[] args) {
        JobConfig c = new JobConfig();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("--input".equals(a)) {
                c.input = value(args, ++i, a);
            } else if ("--host".equals(a)) {
                c.host = value(args, ++i, a);
            } else if ("--port".equals(a)) {
                c.port = Integer.parseInt(value(args, ++i, a));
            } else if ("--user".equals(a)) {
                c.user = value(args, ++i, a);
            } else if ("--password".equals(a)) {
                c.password = value(args, ++i, a);
            } else if ("--database".equals(a)) {
                c.database = value(args, ++i, a);
            } else if ("--reset".equals(a)) {
                c.reset = true;
            } else if ("--seed".equals(a)) {
                c.seed = true;
            } else if ("--help".equals(a) || "-h".equals(a)) {
                usage();
                System.exit(0);
            } else {
                throw new IllegalArgumentException("未知参数：" + a);
            }
        }
        return c;
    }

    public String jdbcUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useUnicode=true&characterEncoding=utf8&useSSL=false"
                + "&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
                // 批处理写 5 万多行，开启批重写能把逐条 round-trip 合成多值 INSERT。
                + "&rewriteBatchedStatements=true";
    }

    private static String defaultPassword() {
        String env = System.getenv("DEMO01_DB_PASSWORD");
        return env == null || env.isEmpty() ? "123456" : env;
    }

    private static String value(String[] args, int i, String name) {
        if (i >= args.length) {
            throw new IllegalArgumentException(name + " 缺少取值");
        }
        return args[i];
    }

    private static void usage() {
        System.out.println("用法：java -jar flink-job-0.1.0.jar [选项]");
        System.out.println("  --input <目录>     默认 mock/out");
        System.out.println("  --host <主机>      默认 127.0.0.1");
        System.out.println("  --port <端口>      默认 3306");
        System.out.println("  --user <用户>      默认 root");
        System.out.println("  --password <口令>  默认环境变量 DEMO01_DB_PASSWORD 或 123456");
        System.out.println("  --database <库>    默认 demo01");
        System.out.println("  --reset            跑之前清空行为三表");
        System.out.println("  --seed             重灌 t_account / t_package 种子");
    }

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public boolean isReset() {
        return reset;
    }

    public void setReset(boolean reset) {
        this.reset = reset;
    }

    public boolean isSeed() {
        return seed;
    }

    public void setSeed(boolean seed) {
        this.seed = seed;
    }
}
