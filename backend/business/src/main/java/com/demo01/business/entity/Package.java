package com.demo01.business.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * `t_package` 套餐表（《开发文档》§5.2 ②）。
 *
 * <p>⚠️ 类名与 {@code java.lang.Package} 同名，靠的是「同包优先于自动导入」——
 * 本包内出现的 {@code Package} 一律指本类。这是 Java 允许的写法，但**不必去
 * 引用 {@code java.lang.Package}**（本项目没有那个需求）；将来若真要引用，
 * 写全限定名即可。
 */
@Data
@TableName("t_package")
public class Package {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private BigDecimal monthlyFee;

    /** 流量额度（GB）——与 `t_usage.used_data` 同单位（决策「套餐量纲单位定稿」）。 */
    private BigDecimal dataQuota;

    /** 通话额度（分钟） */
    private Integer voiceQuota;

    /** 短信额度（条） */
    private Integer smsQuota;

    private String description;

    /** 1 在售 / 0 停售。⚠️ 停售的套餐**仍然要能被查到**——老用户还在用。 */
    private Integer status;

    private LocalDateTime createTime;
}
