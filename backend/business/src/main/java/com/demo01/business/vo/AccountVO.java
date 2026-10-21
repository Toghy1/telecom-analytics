package com.demo01.business.vo;

import com.demo01.business.common.MaskUtil;
import com.demo01.business.entity.Account;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 账号出参。
 *
 * <p>⚠️ **没有 {@code password} 字段**，且 {@code login_name} / {@code id_card}
 * 在这里就已经是脱敏态（选型⑥）。这不是「忘了加」——实体里那两个字段是明文的，
 * 直接把实体返回出去等于把密码哈希和完整身份证一起交给浏览器。
 *
 * <p>⚠️ 非 USER 角色（ANALYST / ADMIN）的身份字段为 {@code null}，如实返回。
 * 「最小必要」原则下分析员本就拿不到个人字段，返回 null 比编一个空字符串诚实。
 */
public record AccountVO(
        Long id,
        String loginName,
        String role,
        String idCard,
        String region,
        Long packageId,
        BigDecimal balance,
        Integer status,
        LocalDateTime createTime) {

    public static AccountVO from(Account account) {
        return new AccountVO(
                account.getId(),
                MaskUtil.loginName(account.getLoginName()),
                account.getRole(),
                MaskUtil.idCard(account.getIdCard()),
                account.getRegion(),
                account.getPackageId(),
                account.getBalance(),
                account.getStatus(),
                account.getCreateTime());
    }
}
