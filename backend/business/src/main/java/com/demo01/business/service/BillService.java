package com.demo01.business.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.demo01.business.common.PageQuery;
import com.demo01.business.common.PageResult;
import com.demo01.business.entity.Bill;
import com.demo01.business.mapper.BillMapper;
import com.demo01.business.vo.BillMonthRow;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 费用流水查询（《开发文档》§5.2 ④ 的三个口径，本类一一对应）。
 *
 * <p>⚠️ **每个查询都按 {@code user_id} 过滤**，id 取当前登录账号（来自 JWT）。
 * 少这一条不会有任何报错，只是把别人的流水一起返回——数字更大、看着更「真」。
 */
@Service
public class BillService {

    /** `type` 取值。BILL=消费，PAY=缴费。 */
    private static final String TYPE_BILL = "BILL";
    private static final String TYPE_PAY = "PAY";

    private final BillMapper billMapper;

    public BillService(BillMapper billMapper) {
        this.billMapper = billMapper;
    }

    /** 账户总览的「最近账单」：不分页，只要前 N 条。 */
    public List<Bill> recent(long userId, int limit) {
        return billMapper.selectPage(PageQuery.first(limit),
                        Wrappers.<Bill>lambdaQuery()
                                .eq(Bill::getUserId, userId)
                                .eq(Bill::getType, TYPE_BILL)
                                .orderByDesc(Bill::getOccurTime))
                .getRecords();
    }

    /** 话费查询 · 缴费记录（分页）。 */
    public PageResult<Bill> payments(long userId, int page, int size) {
        Page<Bill> p = billMapper.selectPage(PageQuery.of(page, size),
                Wrappers.<Bill>lambdaQuery()
                        .eq(Bill::getUserId, userId)
                        .eq(Bill::getType, TYPE_PAY)
                        .orderByDesc(Bill::getOccurTime));
        return PageResult.of(p);
    }

    /**
     * 账单查询 · 按月列表。
     *
     * <p>不分页：账期数 = 入网月数 / 12，演示数据只有几个月，给它套分页是多余动作。
     */
    public List<BillMonthRow> months(long userId) {
        return billMapper.selectMonths(userId);
    }

    /** 账单查询 · 某账期明细（分页）。 */
    public PageResult<Bill> list(long userId, String month, int page, int size) {
        Page<Bill> p = billMapper.selectPage(PageQuery.of(page, size),
                Wrappers.<Bill>lambdaQuery()
                        .eq(Bill::getUserId, userId)
                        .eq(Bill::getType, TYPE_BILL)
                        .eq(Bill::getBillMonth, month)
                        .orderByDesc(Bill::getOccurTime));
        return PageResult.of(p);
    }
}
