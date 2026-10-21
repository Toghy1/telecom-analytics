package com.demo01.business.vo;

import com.demo01.business.common.MaskUtil;
import com.demo01.business.entity.Account;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 管理后台账号列表出参。
 *
 * <p>比前端自服务用的 {@code AccountVO} 多一个 {@code packageName}：管理端
 * 列表要显示「当前套餐」的名字，而 {@code t_account} 里只有 {@code package_id}。
 * 名字在 service 里用 {@code selectBatchIds} 批量解析（**不能用「在售套餐列表」**，
 * 已停售的套餐仍被存量用户持有，见前端 {@code api/order.js#resolvePackage}）。
 *
 * <p>⚠️ {@code loginName} / {@code idCard} 在这里已是脱敏态（选型⑥）。
 * 明文手机号只在「新增账号」的请求体里出现一次，之后任何出参都不带它。
 *
 * <p>⚠️ 分析员与管理员的 {@code idCard} / {@code packageId} / {@code packageName} /
 * {@code balance} 为 {@code null}——「最小必要」原则，前端显示 {@code '--'}。
 * 不要在这里补 0 或空串：那会让「没有余额这个概念」看起来像「余额 0 元」。
 */
public record AdminAccountVO(
        Long id,
        String loginName,
        String role,
        String idCard,
        String region,
        Long packageId,
        String packageName,
        BigDecimal balance,
        Integer status,
        LocalDateTime createTime) {

    public static AdminAccountVO from(Account account, String packageName) {
        return new AdminAccountVO(
                account.getId(),
                MaskUtil.loginName(account.getLoginName()),
                account.getRole(),
                MaskUtil.idCard(account.getIdCard()),
                account.getRegion(),
                account.getPackageId(),
                packageName,
                account.getBalance(),
                account.getStatus(),
                account.getCreateTime());
    }
}
