package com.demo01.realtime.metric;

import java.util.List;

/**
 * 24 小时时段热力图：近 7 天 × 24 小时的事件数。
 *
 * @param days   纵轴日期标签，`M/d` 格式，**从 6 天前到今天**（`days[6]` 是今天）
 * @param series 扁平点集，每个点是 `[hourIndex, dayIndex, count]`，
 *               ECharts 按坐标落格，顺序无关
 *
 * ⚠️ `count` 允许为 **null**：今天**尚未到来**的时段是「还不知道」，
 *    不是「0」。若补 0，热力图上今天剩下的半天会被涂成浅色，
 *    看起来像「用户半夜都不上网」，而事实是那些小时还没发生。
 *    已过去但确实没人访问的小时仍然是 0，两者必须区分开。
 *    消费端用 `Arrays.asList`（允许 null）而不是 `List.of`（遇 null 抛 NPE）。
 */
public record Heatmap(List<String> days, List<List<Object>> series) {
}
