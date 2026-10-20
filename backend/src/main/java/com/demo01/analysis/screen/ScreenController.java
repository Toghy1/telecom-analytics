package com.demo01.analysis.screen;

import com.demo01.analysis.common.PageResult;
import com.demo01.analysis.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 分析平台只读接口。
 *
 * 共三组：
 *   ① 大屏离线指标（板块③④）
 *   ② 子页管道聚合（今日管道量 / 脏数据趋势 / 规则命中）
 *   ③ 子页死信分页
 *
 * 子页的四块内容没有拆成四个独立微接口——它们是同一份批次日数据的不同切面，
 * 一次查询返回可让页面一次刷新拿全，也避免四个接口各算一遍「批次日」。
 */
@RestController
@RequestMapping("/api/screen")
public class ScreenController {

    private final ScreenService service;

    public ScreenController(ScreenService service) {
        this.service = service;
    }

    /** ① 大屏离线指标：一次拉全，前端按 metric_code 分组。 */
    @GetMapping("/offline-metrics")
    public Result<List<OfflineMetric>> offlineMetrics() {
        return Result.ok(service.offlineMetrics());
    }

    /** ② 子页管道聚合。 */
    @GetMapping("/pipeline/summary")
    public Result<PipelineSummary> pipelineSummary() {
        return Result.ok(service.summary());
    }

    /** ③ 死信分页。`source` 为空表示不过滤。 */
    @GetMapping("/dead-letters")
    public Result<PageResult<DeadLetter>> deadLetters(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(required = false) String source) {
        return Result.ok(service.deadLetters(page, size, source));
    }
}
