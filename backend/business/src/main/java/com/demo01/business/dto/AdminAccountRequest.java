package com.demo01.business.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 管理后台「新增账号」入参，字段与前端 {@code views/admin/Users.vue} 的表单一致。
 *
 * <p>⚠️ {@code loginName} 是**明文**（电信用户填手机号）——它只在请求体里出现一次，
 * 入库时原样写 {@code t_account.login_name}，出参由 {@code AdminAccountVO} 脱敏。
 * 不在入库时脱敏：那一列要参与登录比对，存成 {@code 138****1234} 就再也登不上了。
 *
 * <p>⚠️ 非 USER 角色**不该有** {@code idCard} / {@code packageId} / {@code balance}
 * （角色表的「最小必要」）。这里不写禁止非空，而是让 service 在非 USER 时
 * 一律忽略这三个字段——表单本来就不显示它们，接口再宽容也不会造成脏数据。
 * 校验文案与假数据阶段逐字一致，接线前后管理员看到的提示不该变。
 */
@Data
public class AdminAccountRequest {

    private String loginName;

    /** USER / ANALYST / ADMIN */
    private String role;

    private String region;

    private String idCard;

    private Long packageId;

    private BigDecimal balance;
}
