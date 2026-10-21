package com.demo01.business.vo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 余量出参（决策「余量计算落在后端，前端不做减法」）。
 *
 * <p>⚠️ {@code quota / used / remain} **三个数都给**，而不是只给 {@code remain}。
 * 页面既要用「剩余」也要画进度条（需要 used/quota），只给余量会让前端
 * 反过来做加法，等于把那条减法又搬回前端一次。
 *
 * <p>⚠️ {@code remain} **照实为负**：降档后新套餐额度低于本月已用量是真的会负数，
 * 进度条可以封顶 100%，数字不能撒谎——页面据此把超套的那一条染红。
 */
public record RemainingVO(
        String usageMonth,
        Long packageId,
        String packageName,
        boolean hasUsage,
        List<Item> items) {

    /** 一个量纲（流量 / 通话 / 短信）。 */
    public record Item(
            String key,
            String label,
            String unit,
            BigDecimal quota,
            BigDecimal used,
            BigDecimal remain,
            int percent) {
    }
}
