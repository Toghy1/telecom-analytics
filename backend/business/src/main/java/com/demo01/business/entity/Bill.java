package com.demo01.business.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * `t_bill` 费用流水表（《开发文档》§5.2 ④）。
 *
 * <p>⚠️ 金额一律**存正数**，方向由 {@code type} 区分（BILL 消费 / PAY 缴费）。
 * 靠符号表意会在聚合时算错，且算错不报错。
 */
@Data
@TableName("t_bill")
public class Bill {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** BILL 消费 / PAY 缴费 */
    private String type;

    private BigDecimal amount;

    private LocalDateTime occurTime;

    /** 账期 `2026-09`，仅消费（BILL）有值。 */
    private String billMonth;

    private String remark;

    private LocalDateTime createTime;
}
