package com.demo01.business.controller;

import com.demo01.business.auth.RequireRole;
import com.demo01.business.common.PageResult;
import com.demo01.business.common.Result;
import com.demo01.business.dto.TicketStatusRequest;
import com.demo01.business.service.AdminTicketService;
import com.demo01.business.vo.AdminTicketVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 · 工单管理（《开发文档》§6.6）。
 *
 * <p>⚠️ 这里只有「列表」与「流转」两个接口，**没有修改工单内容的接口**：
 * 工单正文是用户提交的证据，管理员只改状态。
 */
@RestController
@RequestMapping("/api/admin/tickets")
@RequireRole("ADMIN")
public class AdminTicketController {

    private final AdminTicketService adminTicketService;

    public AdminTicketController(AdminTicketService adminTicketService) {
        this.adminTicketService = adminTicketService;
    }

    /** 全部工单（不按 user_id 过滤——这是管理端与用户端的关键差别）。 */
    @GetMapping
    public Result<PageResult<AdminTicketVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return Result.ok(adminTicketService.list(page, size, type, status, keyword));
    }

    /**
     * 状态流转。合法目标只有当前状态的唯一后继（PENDING → PROCESSING →
     * DONE），非法迁移由 service 拒绝——不能把这一步简化成「下拉框改值」。
     */
    @PutMapping("/{id}/status")
    public Result<AdminTicketVO> advance(@PathVariable Long id,
                                         @RequestBody TicketStatusRequest request) {
        return Result.ok(adminTicketService.advance(id, request.getStatus()));
    }
}
