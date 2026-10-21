package com.demo01.business.controller;

import com.demo01.business.auth.LoginContext;
import com.demo01.business.common.PageResult;
import com.demo01.business.common.Result;
import com.demo01.business.entity.Bill;
import com.demo01.business.service.BillService;
import com.demo01.business.vo.BillMonthRow;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;

/**
 * 费用流水查询（《开发文档》§5.2 ④ 的三个口径）。
 *
 * <p>⚠️ 四个接口**都不接收 userId**：用户身份来自 token。多一个参数，
 * 就等于允许任何人查任何人的账单。
 */
@RestController
@RequestMapping("/api/bills")
public class BillController {

    private final BillService billService;

    public BillController(BillService billService) {
        this.billService = billService;
    }

    /** 账户总览的最近账单。不分页，返回数组。 */
    @GetMapping("/recent")
    public Result<List<Bill>> recent(@RequestParam(defaultValue = "3") int limit) {
        return Result.ok(billService.recent(LoginContext.userId(), limit));
    }

    /** 话费查询 · 缴费记录（分页）。 */
    @GetMapping("/payments")
    public Result<PageResult<Bill>> payments(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "5") int size) {
        return Result.ok(billService.payments(LoginContext.userId(), page, size));
    }

    /** 账单查询 · 按月列表（不分页）。 */
    @GetMapping("/months")
    public Result<List<BillMonthRow>> months() {
        return Result.ok(billService.months(LoginContext.userId()));
    }

    /** 账单查询 · 某账期明细（分页）。不传 month 时取服务端当前月。 */
    @GetMapping
    public Result<PageResult<Bill>> list(@RequestParam(required = false) String month,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "5") int size) {
        String billMonth = (month == null || month.isBlank()) ? YearMonth.now().toString() : month.trim();
        return Result.ok(billService.list(LoginContext.userId(), billMonth, page, size));
    }
}
