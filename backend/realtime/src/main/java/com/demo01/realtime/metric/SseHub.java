package com.demo01.realtime.metric;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSE 连接登记处：谁在看着大屏，就往谁那儿推。
 *
 * 为什么不是前端轮询：五个数字卡要**同时**刷新，轮询只能做到「各自 2 秒拉一次」，
 * 五个数字的统计时刻会错开，看起来像数据在互相打架（决策「大屏采用 SSE」）。
 *
 * 为什么不用 WebSocket：这里只有服务端 → 客户端一个方向的数据，SSE 是
 * 普通 HTTP、能过 Nginx、断线自动重连，比 WebSocket 少一整层握手与心跳。
 * 代价是**不能用浏览器原生 `EventSource`**——它带不了 `Authorization`
 * 头，token 只能塞 URL，而 URL 会进 access log（选型⑤）。所以前端用
 * `@microsoft/fetch-event-source`，后端这里就只是个普通 `text/event-stream`。
 *
 * ⚠️ emitter 是**有状态的长连接**，不是线程安全的共享对象。新增/移除都走
 *    `CopyOnWriteArrayList`：推送每 2 秒一次、遍历频繁而增删稀少，正是它的
 *    适用场景；用 `synchronized` 包住整个推送循环会让一个慢客户端卡住所有人。
 */
@Component
public class SseHub {

    private static final Logger log = LoggerFactory.getLogger(SseHub.class);

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ObjectMapper mapper;

    public SseHub(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** 新建一个订阅。`0L` = 不超时：大屏可能挂着好几节课。 */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);

        // 三种"连接没了"的情形都要摘掉，否则列表只增不减，
        // 每次推送都在往一堆死连接上写，写一次抛一次异常。
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        log.info("大屏订阅接入，当前在线 {} 个", emitters.size());
        return emitter;
    }

    /** 推一条指标。没有订阅者时直接返回——没人看大屏时不该有序列化开销。 */
    public void push(MetricRecord record) {
        if (emitters.isEmpty()) {
            return;
        }
        String json;
        try {
            json = mapper.writeValueAsString(record);
        } catch (Exception e) {
            log.warn("指标序列化失败：{}", e.getMessage());
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().data(json, MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                // 客户端已断开：摘掉即可，不是错误。
                emitters.remove(emitter);
            }
        }
    }

    public int size() {
        return emitters.size();
    }
}
