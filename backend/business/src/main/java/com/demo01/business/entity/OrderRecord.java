package com.demo01.business.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * `t_order` 套餐办理记录（《开发文档》§5.2 ⑤）。
 *
 * <p>类名不叫 {@code Order} 是因为 {@code order} 在 SQL 里是关键字、在 MyBatis-Plus
 * 的 Wrapper 里也容易与 {@code orderBy} 混淆；表名仍由 {@code @TableName} 指定。
 *
 * <p>⚠️ {@code status} 固定 1（已生效），**不做状态流转**——决策「套餐办理即时生效」。
 */
@Data
@TableName("t_order")
public class OrderRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long packageId;

    private LocalDateTime orderTime;

    private Integer status;
}
