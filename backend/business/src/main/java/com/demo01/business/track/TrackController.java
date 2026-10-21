package com.demo01.business.track;

import com.demo01.business.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 埋点上报接口（`frontend/src/utils/tracker.js` 的唯一出口）。
 *
 * 职责只有两句话：
 *
 *   ① 请求体是合法 JSON → 投进 Kafka 的 `behavior-log`；
 *   ② 不是合法 JSON → 直接回业务错误，**不入 Kafka**。
 *
 * 为什么「不是 JSON」要在这里就挡掉：Kafka 里躺一条二进制垃圾，消费端读出来
 * 连字段都取不到，只能整条塞死信，`fail_reason` 也说不清是哪条规则——
 * 而死信表的口径是「七条规则之一」（`DIRTY_RULE` 的枚举）。**JSON 可解析性
 * 是传输层问题，字段合法性才是数据层问题**，两者由不同的人负责、在不同的
 * 地方判定，混在一起会让「TYPE_ERROR」既可能是请求发错了，也可能是数据脏了。
 *
 * ⚠️ 刻意**不做**的事：
 *   - 不查库、不验 token：埋点由**游客**也会产生（漏斗第一段 `view_home`），
 *     要求登录会把漏斗第一段整段丢掉（角色闸在前端 `producesBehaviorData()`）。
 *   - 不判 `event_code` 是否在 12 个之内、不判时间格式：这些是七条规则的
 *     第②③条，归实时消费服务判，判错了才进死信——若这里也判一遍，
 *     同一个注入用例会一次落死信、一次被这里 4xx 拒掉，死信分布就对不上。
 *
 * 投递失败（Kafka 不在线）只记日志、仍然回 200：埋点是**旁路**，它不能
 * 因为数据链路故障把用户的正常操作搞挂。宁可丢几条埋点，不能丢一个订单。
 */
@RestController
@RequestMapping("/api/track")
public class TrackController {

    private static final Logger log = LoggerFactory.getLogger(TrackController.class);

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final String topic;

    public TrackController(KafkaTemplate<String, String> kafka,
                           ObjectMapper mapper,
                           @Value("${demo01.kafka.topic:behavior-log}") String topic) {
        this.kafka = kafka;
        this.mapper = mapper;
        this.topic = topic;
    }

    @PostMapping
    public Result<Void> track(@RequestBody String body) {
        try {
            // 只要求「能解析成 JSON 对象」；数组/标量视为非法上报。
            if (!mapper.readTree(body).isObject()) {
                return Result.fail(1001, "埋点内容必须是 JSON 对象");
            }
        } catch (Exception e) {
            return Result.fail(1001, "埋点内容不是合法 JSON");
        }

        try {
            kafka.send(topic, body).whenComplete((meta, ex) -> {
                if (ex != null) {
                    log.warn("埋点投递 Kafka 失败：{}", ex.getMessage());
                }
            });
        } catch (Exception e) {
            // `max.block.ms` 到点仍拿不到 broker 元数据时，send 会同步抛异常。
            // 这里吞掉并记日志：埋点失败不影响用户操作，也不该让页面弹红条。
            log.warn("埋点投递 Kafka 失败（同步）：{}", e.getMessage());
        }

        return Result.ok(null);
    }
}
