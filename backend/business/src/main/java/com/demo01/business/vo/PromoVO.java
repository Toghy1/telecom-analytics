package com.demo01.business.vo;

import java.time.LocalDateTime;

/**
 * 促销活动出参。
 *
 * <p>⚠️ {@code package_name} 由 {@code PromoService} 用一次
 * {@code selectBatchIds} 批量带出（不是 SQL JOIN，也不是逐条查），
 * **不让前端拿到 package_id 再逐条查一次**——那是 N+1 次请求
 * （决策「游客端采用『首页节选 + 浏览页全量』」影响范围第 2 条）。
 */
public record PromoVO(
        Long id,
        String title,
        String content,
        Long packageId,
        String packageName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer status) {
}
