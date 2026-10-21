package com.demo01.business.controller;

import com.demo01.business.auth.LoginContext;
import com.demo01.business.common.PageResult;
import com.demo01.business.common.Result;
import com.demo01.business.dto.TicketRequest;
import com.demo01.business.entity.Ticket;
import com.demo01.business.service.TicketService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 工单提交与查询（《开发文档》§6.4 #6）。 */
@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping
    public Result<PageResult<Ticket>> list(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "5") int size) {
        return Result.ok(ticketService.list(LoginContext.userId(), page, size));
    }

    /** 提交工单。出参是新建的那条记录（含自增 id），页面据此刷新列表。 */
    @PostMapping
    public Result<Ticket> submit(@RequestBody TicketRequest request) {
        return Result.ok(ticketService.submit(LoginContext.userId(), request));
    }
}
