package com.demo01.business.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** `t_account` 账号表（《开发文档》§5.2 ①）。 */
@Data
@TableName("t_account")
public class Account {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户填手机号，管理员填用户名。**明文**，只在登录比对时用到，绝不出参。 */
    private String loginName;

    /** BCrypt 哈希。出参里没有这个字段——见 `AccountVO`。 */
    private String password;

    /** USER / ANALYST / ADMIN */
    private String role;

    /** 身份证，**明文入库**，出参脱敏（决策「身份证脱敏格式定稿」）。 */
    private String idCard;

    /** 归属地，省级名。 */
    private String region;

    /** 当前套餐，一用户一套餐（决策「套餐模型为一用户一套餐」）。 */
    private Long packageId;

    private BigDecimal balance;

    /** 1 启用 / 0 停用。⚠️ 这是业务启停，不是删除标记，故不用 @TableLogic。 */
    private Integer status;

    private LocalDateTime createTime;
}
