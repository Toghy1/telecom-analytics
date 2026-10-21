package com.demo01.business.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色限制（选型③：`@RequireRole` 注解 + 拦截器）。
 *
 * <p>⚠️ **不存在「什么都不写就默认全角色可访问」的隐式规则**——反过来才对：
 * 没写注解的接口只要求「已登录」。所以管理类接口**必须显式标注**，
 * 漏标的表现是「分析员也能调管理接口」，而那不会报任何错。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    /** 允许的角色，如 {@code {"ADMIN"}}。 */
    String[] value();
}
