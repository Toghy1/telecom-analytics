package com.demo01.business.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录入参：{@code {login_name, password}}。
 *
 * <p>⚠️ 字段名在 JSON 里是 {@code login_name}（全局 SNAKE_CASE 策略负责转换），
 * 与前端 {@code api/auth.js#login} 的入参一字不差。
 *
 * <p>⚠️ 校验只做「非空」。「账号或密码不正确」由 service 统一报——
 * 不区分「用户不存在」和「密码错」，否则等于给了一个枚举账号的接口。
 */
@Data
public class LoginRequest {

    @NotBlank(message = "请输入账号")
    private String loginName;

    @NotBlank(message = "请输入密码")
    private String password;
}
