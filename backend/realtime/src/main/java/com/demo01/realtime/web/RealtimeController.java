package com.demo01.realtime.web;

import com.demo01.realtime.common.Result;
import com.demo01.realtime.metric.EventCount;
import com.demo01.realtime.metric.Heatmap;
import com.demo01.realtime.metric.MetricRecord;
import com.demo01.realtime.metric.MetricService;
import com.demo01.realtime.metric.PageHeatRow;
import com.demo01.realtime.metric.SseHub;
import com.demo01.realtime.metric.TrendPoint;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 实时接口。全是只读，前端 `src/api/realtime.js` 与 `src/api/screen.js`
 * 一一对应。
 *
 * <p>数据由 Flink 的 `RealtimeJob` 写入 `dwd_behavior_log`；本服务只查库、
 * 不消费 Kafka，也不做七条判脏。
 *
 * ⚠️ `/metrics` 之外的四根是**普通 JSON**，不是 SSE：它们是"一打开大屏就
 *    各拉一次"的初值，拉完就不变了（变化走 SSE）。全部做成 SSE 会让
 *    重连逻辑散在四个地方，而它们本来就不需要"推"。
 */
@RestController
@RequestMapping("/api/realtime")
public class RealtimeController {

    private final MetricService metrics;
    private final SseHub hub;

    public RealtimeController(MetricService metrics, SseHub hub) {
        this.metrics = metrics;
        this.hub = hub;
    }

    /**
     * SSE 推送。每条消息一条 `MetricRecord` 的 JSON。
     *
     * `produces` 必须显式声明，否则浏览器侧 `fetch` 拿到的是
     * `application/json`，`@microsoft/fetch-event-source` 会直接拒绝连接
     * （它只接受 `text/event-stream`）。
     */
    @GetMapping(value = "/metrics", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter metrics() {
        return hub.subscribe();
    }

    /** 五个指标的当前值。**必须与 `/metrics` 配对使用**，否则首帧是空的。 */
    @GetMapping("/snapshot")
    public Result<List<MetricRecord>> snapshot() {
        return Result.ok(metrics.snapshot());
    }

    /** 访问趋势，`range` 取 `1h` / `24h`。 */
    @GetMapping("/trend")
    public Result<List<TrendPoint>> trend(@RequestParam(defaultValue = "1h") String range) {
        return Result.ok(metrics.trend(range));
    }

    /** 页面热度 Top N。`limit` 由 `LIMIT` 生效，前端不再切片。 */
    @GetMapping("/page-heat")
    public Result<List<PageHeatRow>> pageHeat(@RequestParam(defaultValue = "10") int limit) {
        return Result.ok(metrics.pageHeat(limit));
    }

    /** 今日 12 个事件的计数。 */
    @GetMapping("/event-counts")
    public Result<List<EventCount>> eventCounts() {
        return Result.ok(metrics.eventCounts());
    }

    /** 近 7 天 × 24 小时热力图。 */
    @GetMapping("/hourly-heatmap")
    public Result<Heatmap> hourlyHeatmap() {
        return Result.ok(metrics.hourlyHeatmap());
    }

    /** 当前订阅数，排障用（大屏上没数字时先看这里是不是 0）。 */
    @GetMapping("/clients")
    public Result<Integer> clients() {
        return Result.ok(hub.size());
    }
}
