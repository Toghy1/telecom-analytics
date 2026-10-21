package com.demo01.business.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 办理记录出参。
 *
 * <p>⚠️ 带上 {@code package_name / monthly_fee}：办理记录里躺着的是**当年办过的**
 * 套餐，其中有已下架的。解析时**不能用「在售套餐列表」那个查询**（它过滤
 * {@code status = 1}），否则那条记录会显示成空白——不报错，只是少了个套餐名。
 */
public record OrderVO(
        Long id,
        Long userId,
        Long packageId,
        String packageName,
        BigDecimal monthlyFee,
        LocalDateTime orderTime,
        Integer status) {
}
