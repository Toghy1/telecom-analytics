package com.demo01.business.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.demo01.business.common.BizException;
import com.demo01.business.common.PageQuery;
import com.demo01.business.common.PageResult;
import com.demo01.business.entity.Account;
import com.demo01.business.entity.OrderRecord;
import com.demo01.business.entity.Package;
import com.demo01.business.mapper.AccountMapper;
import com.demo01.business.mapper.OrderRecordMapper;
import com.demo01.business.mapper.PackageMapper;
import com.demo01.business.vo.OrderSubmitVO;
import com.demo01.business.vo.OrderVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 套餐办理与办理记录（《开发文档》§6.4 #5）。
 *
 * <p>语义按 2026-09-30 决策「套餐办理即时生效」：
 * <ul>
 *   <li>{@code t_account.package_id} **立即改**；</li>
 *   <li>{@code t_order.status} 固定 1（已生效），不做状态流转；</li>
 *   <li>**本月不另收月费**——当月账单已有一笔月费。</li>
 * </ul>
 * 连带后果（刻意的）：降档时新额度可能低于本月已用量，余量显示负数并走红色超套样式。
 */
@Service
public class OrderService {

    /** 1 = 已生效。办理页面在降档前会二次确认，这里不再分状态。 */
    private static final int STATUS_EFFECTIVE = 1;

    private static final String UNKNOWN_PACKAGE = "未知套餐";

    private final OrderRecordMapper orderMapper;
    private final PackageMapper packageMapper;
    private final AccountMapper accountMapper;

    public OrderService(OrderRecordMapper orderMapper, PackageMapper packageMapper,
                        AccountMapper accountMapper) {
        this.orderMapper = orderMapper;
        this.packageMapper = packageMapper;
        this.accountMapper = accountMapper;
    }

    /**
     * 办理 / 变更套餐。
     *
     * <p>⚠️ {@code @Transactional} 不是装饰：这里要写**两张表**（改账号的当前套餐 +
     * 追加一条办理记录）。第二步失败而第一步已提交，用户会处在「办过但记录里没有」
     * 的状态——页面上的办理记录与「当前套餐」互相矛盾。
     */
    @Transactional
    public OrderSubmitVO submit(long userId, Long packageId) {
        Package pkg = packageMapper.selectById(packageId);
        if (pkg == null) {
            throw new BizException("套餐不存在");
        }
        // 用 != 1 而不是"非 0"：status 是 1 在售 / 0 下架，别的值同样不该放行。
        if (pkg.getStatus() == null || pkg.getStatus() != 1) {
            throw new BizException("该套餐已下架，无法办理");
        }

        Account account = accountMapper.selectById(userId);
        if (account == null) {
            throw new BizException("账号不存在");
        }
        if (Objects.equals(account.getPackageId(), packageId)) {
            throw new BizException("当前已是该套餐，无需重复办理");
        }

        LocalDateTime now = LocalDateTime.now();

        account.setPackageId(packageId);
        accountMapper.updateById(account);

        OrderRecord order = new OrderRecord();
        order.setUserId(userId);
        order.setPackageId(packageId);
        order.setOrderTime(now);
        order.setStatus(STATUS_EFFECTIVE);
        orderMapper.insert(order);

        return new OrderSubmitVO(packageId, pkg.getName(), now);
    }

    /**
     * 办理记录（分页）。
     *
     * <p>⚠️ 套餐名解析走 {@code selectBatchIds} 单查，**不能**用「在售套餐列表」——
     * 那会按 {@code status = 1} 过滤，已下架的老套餐会显示成空白。
     * 查不到套餐时给「未知套餐」，与前端假数据阶段的兜底值一致。
     */
    public PageResult<OrderVO> list(long userId, int page, int size) {
        Page<OrderRecord> p = orderMapper.selectPage(PageQuery.of(page, size),
                Wrappers.<OrderRecord>lambdaQuery()
                        .eq(OrderRecord::getUserId, userId)
                        .orderByDesc(OrderRecord::getOrderTime));

        List<Long> packageIds = p.getRecords().stream()
                .map(OrderRecord::getPackageId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Package> packages = packageIds.isEmpty()
                ? Map.of()
                : packageMapper.selectBatchIds(packageIds).stream()
                        .collect(Collectors.toMap(Package::getId, pkg -> pkg));

        List<OrderVO> rows = p.getRecords().stream().map(o -> {
            Package pkg = packages.get(o.getPackageId());
            return new OrderVO(o.getId(), o.getUserId(), o.getPackageId(),
                    pkg == null ? UNKNOWN_PACKAGE : pkg.getName(),
                    pkg == null ? null : pkg.getMonthlyFee(),
                    o.getOrderTime(), o.getStatus());
        }).toList();

        return PageResult.of(rows, p.getTotal(), p.getPages(), p.getCurrent());
    }
}
