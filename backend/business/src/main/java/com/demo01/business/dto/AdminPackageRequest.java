package com.demo01.business.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 管理后台「新增 / 编辑套餐」入参。
 *
 * <p>⚠️ 新增与编辑**共用同一个 DTO**：两边字段完全一致，分两个类只会让
 * 「新增拦得住、编辑拦不住」这种漏改有机会出现（前端假数据阶段的
 * {@code validatePackageFields} 也是这个理由共用的）。
 *
 * <p>⚠️ 编辑时 {@code createTime} 不在入参里——它是「这个套餐什么时候上架」，
 * 编辑文案不该改它。service 更新时只写业务字段。
 */
@Data
public class AdminPackageRequest {

    private String name;

    /** 月费（元） */
    private BigDecimal monthlyFee;

    /** 流量额度（GB），与 {@code t_usage.used_data} 同单位（决策「套餐量纲单位定稿」）。 */
    private BigDecimal dataQuota;

    /** 通话额度（分钟） */
    private Integer voiceQuota;

    /** 短信额度（条） */
    private Integer smsQuota;

    private String description;

    /** 1 在售 / 0 停售；不传按在售处理（与前端假数据阶段一致）。 */
    private Integer status;
}
