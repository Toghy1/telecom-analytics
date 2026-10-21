package com.demo01.business.vo;

import com.demo01.business.entity.Package;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 管理后台套餐列表出参。
 *
 * <p>比公开的 {@code Package} 实体多一个 {@code userCount}：管理端要靠它
 * 自解释「为什么这个套餐删不掉」——点删除时后端报「已被使用（关联 N 条记录）」，
 * N 就是这一列。没有它，管理员会以为系统无缘无故不让删。
 *
 * <p>{@code userCount} = 当前持有该套餐的账号数 + 历史办理记录数，
 * 与删除守卫的计数**同一口径**（两处若各数各的，会出现「显示 0 却删不掉」）。
 */
public record AdminPackageVO(
        Long id,
        String name,
        BigDecimal monthlyFee,
        BigDecimal dataQuota,
        Integer voiceQuota,
        Integer smsQuota,
        String description,
        Integer status,
        LocalDateTime createTime,
        long userCount) {

    public static AdminPackageVO from(Package pkg, long userCount) {
        return new AdminPackageVO(
                pkg.getId(),
                pkg.getName(),
                pkg.getMonthlyFee(),
                pkg.getDataQuota(),
                pkg.getVoiceQuota(),
                pkg.getSmsQuota(),
                pkg.getDescription(),
                pkg.getStatus(),
                pkg.getCreateTime(),
                userCount);
    }
}
