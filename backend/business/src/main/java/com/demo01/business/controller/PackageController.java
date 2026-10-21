package com.demo01.business.controller;

import com.demo01.business.common.Result;
import com.demo01.business.entity.Package;
import com.demo01.business.service.PackageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 套餐查询。
 *
 * <p>直接返回实体而**没有再套一层 VO**：这张表没有需要脱敏或重命名的字段，
 * 实体的列集合就是接口契约，多写一个字段完全相同的 VO 只是多一处要同步的地方。
 * 需要 VO 的是账号（脱敏）、办理记录（要拼套餐名）、活动（要拼套餐名）那几处。
 */
@RestController
@RequestMapping("/api/packages")
public class PackageController {

    private final PackageService packageService;

    public PackageController(PackageService packageService) {
        this.packageService = packageService;
    }

    /** 在售套餐，按月费升序。{@code limit} 用于首页节选。 */
    @GetMapping
    public Result<List<Package>> list(@RequestParam(required = false) Integer limit) {
        return Result.ok(packageService.list(limit));
    }

    /** 按 id 查单个套餐（**不看 status**）；查不到返回 {@code data: null}。 */
    @GetMapping("/{id}")
    public Result<Package> get(@PathVariable Long id) {
        return Result.ok(packageService.getById(id));
    }
}
