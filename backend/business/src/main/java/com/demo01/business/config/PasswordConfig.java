package com.demo01.business.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 口令比对器。
 *
 * <p>⚠️ 只引了 {@code spring-security-crypto}（一个纯算法包），**没有引
 * spring-security-web**——选型③定的是自研拦截器，不引整套 Spring Security。
 * 这个包提供 BCrypt 而没有自动配置，所以必须在这里手工交出 bean，否则
 * {@code AuthService} 注入不到。
 *
 * <p>⚠️ BCrypt 每次加盐，同一个口令两次编码结果不同，**不能用等号比**，
 * 只能 {@code matches(raw, hash)}。库里那 500 个演示用户的哈希都是
 * {@code 123456} 的 BCrypt 值。
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
