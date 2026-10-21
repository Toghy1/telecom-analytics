package com.demo01.business.controller;

import com.demo01.business.common.Result;
import com.demo01.business.dto.LoginRequest;
import com.demo01.business.service.AuthService;
import com.demo01.business.vo.LoginVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录 / 登出。
 *
 * <p>⚠️ {@code /api/auth/login} 在拦截器的放行清单里（{@code WebConfig}）——
 * 登录接口本身要在没有 token 时可用，不放行就是「永远登不上」。
 * {@code /api/auth/logout} 则在拦截器后面：要退出得先证明自己是登录状态。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    @PostMapping("/logout")
    public Result<Boolean> logout(@RequestHeader(value = "Authorization", required = false) String header) {
        String token = (header != null && header.startsWith(BEARER_PREFIX))
                ? header.substring(BEARER_PREFIX.length()).trim()
                : null;
        authService.logout(token);
        return Result.ok(true);
    }
}
