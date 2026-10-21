package com.demo01.flink.config;

/**
 * 实时作业参数。
 *
 * <pre>
 *   --bootstrap   192.168.23.101:9092   Kafka
 *   --topic       behavior-log
 *   --group       behavior-realtime-flink
 *   --from-earliest                     从 topic 最早的消息开始（默认 latest）
 *   --checkpoint  file:///tmp/flink-checkpoints
 *   --no-checkpoint                     关掉 checkpoint（本地快速试跑用）
 *   --parallelism 1
 *   --host / --port / --user / --password / --database   与离线作业相同
 * </pre>
 *
 * <p>默认用 {@code committedOffsets(LATEST)}：第一次启动从最新消息开始，
 * 不会把 topic 里上一次演示的旧数据重新“实时”推一遍；作业重启后则从
 * checkpoint / 已提交位点继续。
 *
 * <p>⚠️ {@code --host} 是 **Flink 作业所在的机器** 能访问到的 MySQL 地址，
 * 不是开发机的 127.0.0.1。集群提交时务必换成局域网地址。
 */
public class RealtimeConfig extends JobConfig {

    private static final long serialVersionUID = 1L;

    private String bootstrapServers = "192.168.23.101:9092";
    private String topic = "behavior-log";
    private String groupId = "behavior-realtime-flink";
    private boolean fromEarliest;
    private boolean checkpointEnabled = true;
    private String checkpointDir = "file:///tmp/flink-checkpoints";
    private int parallelism = 1;

    public static RealtimeConfig parse(String[] args) {
        RealtimeConfig c = new RealtimeConfig();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("--bootstrap".equals(a)) {
                c.bootstrapServers = value(args, ++i, a);
            } else if ("--topic".equals(a)) {
                c.topic = value(args, ++i, a);
            } else if ("--group".equals(a)) {
                c.groupId = value(args, ++i, a);
            } else if ("--from-earliest".equals(a)) {
                c.fromEarliest = true;
            } else if ("--checkpoint".equals(a)) {
                c.checkpointDir = value(args, ++i, a);
                c.checkpointEnabled = true;
            } else if ("--no-checkpoint".equals(a)) {
                c.checkpointEnabled = false;
            } else if ("--parallelism".equals(a)) {
                c.parallelism = Integer.parseInt(value(args, ++i, a));
            } else if ("--host".equals(a)) {
                c.setHost(value(args, ++i, a));
            } else if ("--port".equals(a)) {
                c.setPort(Integer.parseInt(value(args, ++i, a)));
            } else if ("--user".equals(a)) {
                c.setUser(value(args, ++i, a));
            } else if ("--password".equals(a)) {
                c.setPassword(value(args, ++i, a));
            } else if ("--database".equals(a)) {
                c.setDatabase(value(args, ++i, a));
            } else if ("--help".equals(a) || "-h".equals(a)) {
                usage();
                System.exit(0);
            } else {
                throw new IllegalArgumentException("未知参数：" + a);
            }
        }
        if (c.parallelism < 1) {
            throw new IllegalArgumentException("--parallelism 必须 ≥ 1");
        }
        return c;
    }

    private static String value(String[] args, int i, String name) {
        if (i >= args.length) {
            throw new IllegalArgumentException(name + " 缺少取值");
        }
        return args[i];
    }

    private static void usage() {
        System.out.println("用法：java -cp flink-job-0.1.0.jar com.demo01.flink.RealtimeJob [选项]");
        System.out.println("  --bootstrap <地址>    默认 192.168.23.101:9092");
        System.out.println("  --topic <topic>       默认 behavior-log");
        System.out.println("  --group <group>       默认 behavior-realtime-flink");
        System.out.println("  --from-earliest       从最早消息开始（默认 latest）");
        System.out.println("  --checkpoint <目录>   默认 file:///tmp/flink-checkpoints");
        System.out.println("  --no-checkpoint       关闭 checkpoint");
        System.out.println("  --parallelism <n>     默认 1");
        System.out.println("  --host <主机>         MySQL，默认 127.0.0.1");
        System.out.println("  --port <端口>         默认 3306");
        System.out.println("  --user <用户>         默认 root");
        System.out.println("  --password <口令>     默认环境变量 DEMO01_DB_PASSWORD 或 123456");
        System.out.println("  --database <库>       默认 demo01");
    }

    public String getBootstrapServers() {
        return bootstrapServers;
    }

    public String getTopic() {
        return topic;
    }

    public String getGroupId() {
        return groupId;
    }

    public boolean isFromEarliest() {
        return fromEarliest;
    }

    public boolean isCheckpointEnabled() {
        return checkpointEnabled;
    }

    public String getCheckpointDir() {
        return checkpointDir;
    }

    public int getParallelism() {
        return parallelism;
    }
}
