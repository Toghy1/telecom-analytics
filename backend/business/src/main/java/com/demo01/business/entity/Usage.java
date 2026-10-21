package com.demo01.business.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * `t_usage` 用量表（决策「业务数据模型增补 `t_usage`」）。**汇总式**：一人一月一行。
 *
 * <p>⚠️ 查余量必须**同时按 `user_id` 与 `usage_month` 过滤**。只按月份查会拿到
 * 别人的用量，且不报错——页面会安静地显示另一个人的数字。
 */
@Data
@TableName("t_usage")
public class Usage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 账期 `2026-09`。 */
    private String usageMonth;

    /** 本月已用流量（GB），与 `t_package.data_quota` 同单位。 */
    private BigDecimal usedData;

    private Integer usedVoice;

    private Integer usedSms;

    private LocalDateTime createTime;
}
