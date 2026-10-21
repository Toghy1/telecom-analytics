package com.demo01.business.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.demo01.business.auth.LoginContext;
import com.demo01.business.common.BizException;
import com.demo01.business.common.UnauthorizedException;
import com.demo01.business.entity.Account;
import com.demo01.business.entity.Package;
import com.demo01.business.entity.Usage;
import com.demo01.business.mapper.AccountMapper;
import com.demo01.business.mapper.PackageMapper;
import com.demo01.business.mapper.UsageMapper;
import com.demo01.business.vo.AccountVO;
import com.demo01.business.vo.RemainingVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;

/**
 * 账户总览与余量（《开发文档》§6.4 #1、#4）。
 *
 * <p>⚠️ 余量那条减法放在这里，**不放在前端**（决策「余量计算落在后端」）。
 * 理由见前端 {@code api/usage.js} 顶部：两个数单位不一致时相减不报错、
 * 只是结果错，错成一个看起来很正常的数字。这类错误只有一个入口才好守。
 */
@Service
public class AccountService {

    private final AccountMapper accountMapper;
    private final PackageMapper packageMapper;
    private final UsageMapper usageMapper;

    public AccountService(AccountMapper accountMapper, PackageMapper packageMapper,
                          UsageMapper usageMapper) {
        this.accountMapper = accountMapper;
        this.packageMapper = packageMapper;
        this.usageMapper = usageMapper;
    }

    /** 当前登录用户（id 来自 JWT，不由前端传）。 */
    public Account current() {
        Long userId = LoginContext.userId();
        Account account = userId == null ? null : accountMapper.selectById(userId);
        if (account == null) {
            // 拿着一张合法 token 但库里人没了（被删号）。这与「未登录」不同，
            // 但对前端来说处置一样：清登录态、回登录页。所以走 401。
            throw new UnauthorizedException("账号不存在，请重新登录");
        }
        return account;
    }

    public AccountVO currentVO() {
        return AccountVO.from(current());
    }

    /**
     * 查某账期余量。
     *
     * @param month {@code YYYY-MM}；为空时取服务端当前月（前端假数据阶段
     *              写死了 {@code 2026-09}，接线后由这里说话）。
     */
    public RemainingVO remaining(String month) {
        String usageMonth = (month == null || month.isBlank()) ? YearMonth.now().toString() : month.trim();
        if (!usageMonth.matches("\\d{4}-\\d{2}")) {
            throw new BizException("账期格式不正确");
        }

        Account account = current();
        if (account.getPackageId() == null) {
            throw new BizException("当前账号未订购套餐，无法计算余量");
        }
        Package pkg = packageMapper.selectById(account.getPackageId());
        if (pkg == null) {
            // 账号挂着一个不存在的套餐 id：数据坏了。如实报错，不要静默当 0 额度。
            throw new BizException("当前套餐不存在，请前往营业厅核实");
        }

        // ⚠️ user_id 条件不能少：t_usage 是全体用户共用的表，掉了这个条件
        //    会安静地显示**别人**的用量，不报任何错。
        Usage usage = usageMapper.selectOne(Wrappers.<Usage>lambdaQuery()
                .eq(Usage::getUserId, account.getId())
                .eq(Usage::getUsageMonth, usageMonth));

        boolean hasUsage = usage != null;
        // 「查不到行」按已用 0 处理：t_usage 契约上一人一月一行（造数脚本每月初
        // 为所有在网用户落一行 0 用量），查不到只可能是数据缺失。
        BigDecimal usedData = usage == null ? BigDecimal.ZERO : usage.getUsedData();
        BigDecimal usedVoice = usage == null ? BigDecimal.ZERO : BigDecimal.valueOf(usage.getUsedVoice());
        BigDecimal usedSms = usage == null ? BigDecimal.ZERO : BigDecimal.valueOf(usage.getUsedSms());

        List<RemainingVO.Item> items = List.of(
                item("data", "国内流量", "GB", pkg.getDataQuota(), usedData),
                item("voice", "国内通话", "分钟", BigDecimal.valueOf(pkg.getVoiceQuota()), usedVoice),
                item("sms", "国内短信", "条", BigDecimal.valueOf(pkg.getSmsQuota()), usedSms));

        return new RemainingVO(usageMonth, pkg.getId(), pkg.getName(), hasUsage, items);
    }

    private RemainingVO.Item item(String key, String label, String unit, BigDecimal quota, BigDecimal used) {
        // remain 照实为负：降档后新额度可能低于本月已用量，这是真的超套。
        BigDecimal remain = quota.subtract(used);
        int percent = quota.signum() > 0
                ? used.multiply(BigDecimal.valueOf(100))
                     .divide(quota, 0, RoundingMode.HALF_UP)
                     .min(BigDecimal.valueOf(100))
                     .intValue()
                : 0;
        return new RemainingVO.Item(key, label, unit, quota, used, remain, percent);
    }
}
