package com.demo01.business.controller;

import com.demo01.business.auth.RequireRole;
import com.demo01.business.common.PageResult;
import com.demo01.business.common.Result;
import com.demo01.business.dto.AccountStatusRequest;
import com.demo01.business.dto.AdminAccountRequest;
import com.demo01.business.service.AdminAccountService;
import com.demo01.business.vo.AdminAccountVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 · 用户管理（《开发文档》§6.6）。
 *
 * <p>⚠️ {@code @RequireRole("ADMIN")} 必须写在**类上**：这个控制器里每个方法
 * 都是管理操作，逐个标容易漏；漏标的表现是「分析员也能调管理接口」，
 * 而且不会报任何错（拦截器对没标注解的接口只要求已登录）。
 */
@RestController
@RequestMapping("/api/admin/accounts")
@RequireRole("ADMIN")
public class AdminAccountController {

    private final AdminAccountService adminAccountService;

    public AdminAccountController(AdminAccountService adminAccountService) {
        this.adminAccountService = adminAccountService;
    }

    /**
     * 账号列表。
     *
     * <p>{@code status} 用 {@code Integer}：前端「全部」传的是空串，
     * Spring 会把空串转成 {@code null}；若用 {@code int}，空串会直接报绑定错误。
     */
    @GetMapping
    public Result<PageResult<AdminAccountVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        return Result.ok(adminAccountService.list(page, size, role, status, keyword));
    }

    /** 新增账号。请求体里的手机号是明文，出参只有脱敏后的账号信息。 */
    @PostMapping
    public Result<AdminAccountVO> create(@RequestBody AdminAccountRequest request) {
        return Result.ok(adminAccountService.create(request));
    }

    /** 启用 / 停用。 */
    @PutMapping("/{id}/status")
    public Result<AdminAccountVO> updateStatus(@PathVariable Long id,
                                               @RequestBody AccountStatusRequest request) {
        return Result.ok(adminAccountService.updateStatus(id, request.getStatus()));
    }

    /** 重置密码为初始口令。 */
    @PutMapping("/{id}/password/reset")
    public Result<Boolean> resetPassword(@PathVariable Long id) {
        return Result.ok(adminAccountService.resetPassword(id));
    }

    /** 删除账号。有业务数据的账号会被拒绝，提示改用「停用」。 */
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.ok(adminAccountService.delete(id));
    }
}
