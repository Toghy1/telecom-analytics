package com.demo01.business.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** `t_promo` 促销活动表（《开发文档》§5.2 ③）。 */
@Data
@TableName("t_promo")
public class Promo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String content;

    /** 适用套餐。可为空（全场活动）。出参时 `LEFT JOIN t_package` 解析成名字。 */
    private Long packageId;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** 1 启用 / 0 停用。⚠️ 单看它不够，「进行中」还要判档期（见 PromoService）。 */
    private Integer status;
}
