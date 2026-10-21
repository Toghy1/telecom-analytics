package com.demo01.flink;

import com.demo01.flink.clean.ValidateFunction;
import com.demo01.flink.config.JobConfig;
import com.demo01.flink.config.PackageSeed;
import com.demo01.flink.io.JdbcSupport;
import com.demo01.flink.io.LogFiles;
import com.demo01.flink.io.UserLoader;
import com.demo01.flink.metric.OfflineMetricAggregator;
import com.demo01.flink.model.DirtyRecord;
import com.demo01.flink.model.Event;
import com.demo01.flink.model.RawLine;
import com.demo01.flink.model.User;
import com.demo01.flink.sink.DirtySink;
import com.demo01.flink.sink.DwdSink;
import com.demo01.flink.sink.MetricSink;
import com.demo01.flink.source.LogFileSource;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 离线批处理作业入口。
 *
 * <pre>
 *   日文件 → 校验/判脏 ─┬─ 干净 → dwd_behavior_log
 *                       ├─ 脏   → dwd_behavior_dirty
 *                       └─ 干净 → 指标聚合 → dws_metric_offline
 * </pre>
 *
 * <p>实时链路在 {@link RealtimeJob}：Kafka 消费、同一份七条规则、同样的
 * DWD / 死信表。两个作业共用一个 {@code BehaviorValidator}，这就是
 * 「一个引擎、一份清洗代码、两种执行模式」的落点。
 *
 * <p>运行：
 * <pre>
 *   java -jar flink-job-0.1.0.jar --input "D:/作业/sx/demo01/mock/out" --reset
 * </pre>
 * 在 JDK 8 上跑（Flink 1.17.2 的官方支持版本）。默认本机 MySQL 的
 * {@code demo01} 库，口令取 {@code DEMO01_DB_PASSWORD} 或 123456。
 */
public final class OfflineJob {

    public static void main(String[] args) throws Exception {
        JobConfig config = JobConfig.parse(args);

        List<User> users = UserLoader.load(config.getInput());
        Map<Long, String> packageNames = PackageSeed.names();
        LocalDate statDate = LogFiles.maxDay(config.getInput());

        System.out.println("[flink-job] 用户 " + users.size() + " 人；统计日 " + statDate);
        System.out.println("[flink-job] 输入目录 " + config.getInput());
        System.out.println("[flink-job] MySQL " + config.jdbcUrl());

        if (config.isReset()) {
            JdbcSupport.reset(config);
            System.out.println("[flink-job] 已清空行为三表");
        }
        if (config.isSeed()) {
            JdbcSupport.seed(config, users);
            System.out.println("[flink-job] 已重灌 t_account / t_package");
        }

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // 单并行度：校验规则⑥的「第一条」和全部全局指标都依赖顺序。
        env.setParallelism(1);

        DataStream<RawLine> raw = env.addSource(new LogFileSource(config.getInput()))
                .name("log-file-source")
                .setParallelism(1);

        SingleOutputStreamOperator<Event> clean = raw
                .process(new ValidateFunction())
                .name("validate")
                .setParallelism(1);

        DataStream<DirtyRecord> dirty = clean.getSideOutput(ValidateFunction.DIRTY);

        // 干净事件 → dwd；结束标记只给指标聚合，不能落库。
        clean.filter(new FilterFunction<Event>() {
                    @Override
                    public boolean filter(Event event) {
                        return !event.isEnd();
                    }
                })
                .name("drop-end-marker")
                .addSink(new DwdSink(config))
                .name("dwd-sink")
                .setParallelism(1);

        dirty.addSink(new DirtySink(config))
                .name("dirty-sink")
                .setParallelism(1);

        // 干净事件 → 全局指标；收到结束标记后一次性吐行。
        clean.process(new OfflineMetricAggregator(users, packageNames, statDate))
                .name("offline-metrics")
                .addSink(new MetricSink(config))
                .name("metric-sink")
                .setParallelism(1);

        env.execute("demo01-offline-flink");
        System.out.println("[flink-job] 完成");
    }

    private OfflineJob() {
    }
}
