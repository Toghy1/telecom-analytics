package com.demo01.realtime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 实时应用入口。
 *
 * <p>Kafka 消费、洗净、写 DWD / 死信都在 Flink 的 `RealtimeJob` 里；本服务
 * 只负责「读 MySQL + 推 SSE」。Flink 不能维持浏览器长连接，这一层必须
 * 留在 Spring。
 *
 * <p>`@EnableScheduling` 是给 `MetricService.tick()` 用的——指标每 2 秒
 * 从库里取一次、落一行 `dws_metric_realtime` 并推一次 SSE。推送节奏定在
 * 服务端而不是前端轮询，是因为大屏上五个数字卡必须**同一时刻**刷新
 * （决策「大屏采用 SSE」）。
 */
@SpringBootApplication
@EnableScheduling
public class RealtimeApplication {

    public static void main(String[] args) {
        SpringApplication.run(RealtimeApplication.class, args);
    }
}
