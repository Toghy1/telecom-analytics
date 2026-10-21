package com.demo01.business.auth;

import com.demo01.business.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 认证 + 授权拦截器（选型③）。
 *
 * <p>⚠️ 401 / 403 这里**直接写响应**，不抛异常走 {@code @RestControllerAdvice}。
 * 拦截器在 `preHandle` 阶段抛出的异常虽然一般也能被全局处理器接住，
 * 但那条路径依赖「此时已解析出 handler」这个前提，属于能跑但不必依赖的细节；
 * 直接写响应是**确定的**，且省掉一次异常构造。
 *
 * <p>⚠️ 角色判定只在**写了 `@RequireRole` 的地方**生效，没写的接口只要求已登录。
 * 也就是说「漏标注解」= 该接口对所有已登录角色开放，**不会报错**。
 * 管理类接口必须显式标 `@RequireRole("ADMIN")`。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final TokenStore tokenStore;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(JwtUtil jwtUtil, TokenStore tokenStore, ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.tokenStore = tokenStore;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // CORS 预检不带 Authorization 头，必须放行；拦掉的话浏览器报的会是
        // 一个与鉴权毫无关系的跨域错误，排查方向直接跑偏。
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            write(response, HttpServletResponse.SC_UNAUTHORIZED, "请先登录");
            return false;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        // 先查白名单再验签：退出登录后 token 仍能通过验签（它没过期、签名也对），
        // 白名单才是「已经登出」这个事实的唯一来源。
        if (!tokenStore.contains(token)) {
            write(response, HttpServletResponse.SC_UNAUTHORIZED, "登录已过期，请重新登录");
            return false;
        }

        Claims claims;
        try {
            claims = jwtUtil.parse(token);
        } catch (Exception e) {
            write(response, HttpServletResponse.SC_UNAUTHORIZED, "登录已过期，请重新登录");
            return false;
        }

        long userId = Long.parseLong(claims.getSubject());
        String role = claims.get("role", String.class);
        LoginContext.set(new LoginContext.CurrentUser(userId, role));

        RequireRole required = resolveRequireRole(handler);
        if (required != null && !Arrays.asList(required.value()).contains(role)) {
            write(response, HttpServletResponse.SC_FORBIDDEN, "没有权限执行该操作");
            return false;
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // ⚠️ 必须清：Tomcat 线程复用，不清则下一个请求会读到上一个请求的用户身份。
        LoginContext.clear();
    }

    /** 方法上的注解优先于类上的（同一个 Controller 里不同方法可以要求不同角色）。 */
    private RequireRole resolveRequireRole(Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return null;
        }
        RequireRole onMethod = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (onMethod != null) {
            return onMethod;
        }
        return handlerMethod.getBeanType().getAnnotation(RequireRole.class);
    }

    private void write(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(status, message)));
    }
}
