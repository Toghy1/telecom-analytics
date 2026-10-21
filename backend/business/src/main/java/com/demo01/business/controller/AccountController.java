package com.demo01.business.controller;

import com.demo01.business.common.Result;
import com.demo01.business.service.AccountService;
import com.demo01.business.vo.AccountVO;
import com.demo01.business.vo.RemainingVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 账户总览与余量。
 *
 * <p>两个接口都**不接收 userId**——用户身份只来自 {@code Authorization} 里那个
 * token（拦截器解析后放进 {@code LoginContext}）。接口上多一个 userId 参数，
 * 就等于允许任何人查任何人的余额。
 *
 * <p>这里不标 {@code @RequireRole}：自服务接口对任何已登录角色开放，
 * 且操作对象永远是「我自己」。
 */
@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /** 账户总览。出参已脱敏（手机号前 3 后 4、身份证前 6 后 4）。 */
    @GetMapping
    public Result<AccountVO> me() {
        return Result.ok(accountService.currentVO());
    }

    /**
     * 余量查询。
     *
     * @param month {@code YYYY-MM}，不传取服务端当前月
     */
    @GetMapping("/usage/remaining")
    public Result<RemainingVO> remaining(@RequestParam(required = false) String month) {
        return Result.ok(accountService.remaining(month));
    }
}
