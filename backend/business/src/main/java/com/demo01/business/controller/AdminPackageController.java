package com.demo01.business.controller;

import com.demo01.business.auth.RequireRole;
import com.demo01.business.common.PageResult;
import com.demo01.business.common.Result;
import com.demo01.business.dto.AdminPackageRequest;
import com.demo01.business.service.AdminPackageService;
import com.demo01.business.vo.AdminPackageVO;
import com.demo01.business.vo.OptionVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理后台 · 套餐配置（《开发文档》§6.6）。
 *
 * <p>与公开的 {@code /api/packages} 是**两组不同的接口**，别混：
 * 那组只回答「现在能办哪些」（过滤在售），这组回答「系统里有哪些」（不过滤）。
 */
@RestController
@RequestMapping("/api/admin/packages")
@RequireRole("ADMIN")
public class AdminPackageController {

    private final AdminPackageService adminPackageService;

    public AdminPackageController(AdminPackageService adminPackageService) {
        this.adminPackageService = adminPackageService;
    }

    @GetMapping
    public Result<PageResult<AdminPackageVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        return Result.ok(adminPackageService.list(page, size, status, keyword));
    }

    /** 下拉选项（新增账号挑当前套餐用）。**不过滤 status**，已停售的也要能选。 */
    @GetMapping("/options")
    public Result<List<OptionVO>> options() {
        return Result.ok(adminPackageService.options());
    }

    @PostMapping
    public Result<AdminPackageVO> create(@RequestBody AdminPackageRequest request) {
        return Result.ok(adminPackageService.create(request));
    }

    @PutMapping("/{id}")
    public Result<AdminPackageVO> update(@PathVariable Long id,
                                         @RequestBody AdminPackageRequest request) {
        return Result.ok(adminPackageService.update(id, request));
    }

    /** 删除。有引用会被拒绝，提示改用「停售」。 */
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.ok(adminPackageService.delete(id));
    }
}
