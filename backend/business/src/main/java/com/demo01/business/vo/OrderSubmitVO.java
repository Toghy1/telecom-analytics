package com.demo01.business.vo;

import java.time.LocalDateTime;

/**
 * 办理成功的出参：{@code {package_id, package_name, order_time}}。
 *
 * <p>与前端 {@code api/order.js#submitOrder} 假数据阶段的返回值同形，
 * 办理页拿到它直接弹「已变更为 XXX 套餐」。
 */
public record OrderSubmitVO(Long packageId, String packageName, LocalDateTime orderTime) {
}
