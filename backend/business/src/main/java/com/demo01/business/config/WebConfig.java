package com.demo01.business.config;

import com.demo01.business.auth.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 拦截器注册与放行清单（选型③的「一次性定死」要求）。
 *
 * <p>⚠️ **放行清单漏一项的症状是「全站 401」**，且看不出是哪一项的问题：
 * <ul>
 *   <li>{@code /api/auth/login} —— 登录接口本身要能在没 token 时访问，
 *       漏了它就是「永远登不上」的死锁；</li>
 *   <li>{@code /api/track} —— 埋点对游客开放（游客是漏斗第一段），
 *       漏了它就是「游客一进首页就 401」，而那只会表现为控制台一堆红字；</li>
 *   <li>{@code /api/packages/**} 与 {@code /api/promos/**} —— 「游客端采用
 *       首页节选 + 浏览页全量」：未登录用户要能看套餐与活动。这两个接口
 *       只返回公开的商品信息，不含任何个人字段。漏了它们，首页会出现
 *       两个 401——页面结构完整、只是那两块空的，最容易被当成「数据没造」。</li>
 * </ul>
 * 将来加 Swagger 时，{@code /swagger-ui/**} 与 {@code /v3/api-docs/**}
 * 也要一并加进来。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/track",
                        "/api/packages/**",
                        "/api/promos/**"
                );
    }

    /**
     * 开发期 CORS。
     *
     * <p>⚠️ 前端走 Vite 的 `/api` 代理（同源），**本来用不到 CORS**。
     * 留这一段的唯一用途是「用浏览器或别的端口直连 8082 调试」。
     * 生产由 Nginx 同源反代，这一段届时应当整段删掉——放行所有来源
     * 不是可以长期带着的东西。
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
