package com.demo01.business.controller;

import com.demo01.business.auth.LoginContext;
import com.demo01.business.common.PageResult;
import com.demo01.business.common.Result;
import com.demo01.business.dto.OrderRequest;
import com.demo01.business.service.OrderService;
import com.demo01.business.vo.OrderSubmitVO;
import com.demo01.business.vo.OrderVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 套餐办理与办理记录（《开发文档》§6.4 #5）。 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public Result<PageResult<OrderVO>> list(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "5") int size) {
        return Result.ok(orderService.list(LoginContext.userId(), page, size));
    }

    /** 办理 / 变更套餐。即时生效，本月不另收月费。 */
    @PostMapping
    public Result<OrderSubmitVO> submit(@Valid @RequestBody OrderRequest request) {
        return Result.ok(orderService.submit(LoginContext.userId(), request.getPackageId()));
    }
}
