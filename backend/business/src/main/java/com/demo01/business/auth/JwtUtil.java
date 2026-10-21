package com.demo01.business.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 签发与校验（选型③：自研拦截器 + JWT，不引 Spring Security）。
 *
 * <p>⚠️ jjwt **0.12.x 换了 API**：{@code Jwts.parserBuilder().setSigningKey(...)}
 * 已经是 0.11.x 的写法，0.12 起改为 {@code Jwts.parser().verifyWith(key)}。
 * 团队若照旧教程写，编译能过（旧方法已删，会直接编译失败，算好事），
 * 但混装两个版本的 jjwt-api / jjwt-impl 时会在**运行时**报
 * {@code Unsupported JWT}，那种更难查。
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long ttlMillis;

    public JwtUtil(@Value("${demo01.jwt.secret}") String secret,
                   @Value("${demo01.jwt.ttl-minutes}") long ttlMinutes) {
        // HS256 要求密钥至少 256 位；不够长时 hmacShaKeyFor 会直接抛 WeakKeyException，
        // 这正是希望的行为——弱密钥不该悄悄降级成别的签名算法。
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlMillis = ttlMinutes * 60_000L;
    }

    public long getTtlMillis() {
        return ttlMillis;
    }

    /** 签发。`sub` 放用户 id，`role` 单独成 claim——拦截器验角色时不必再查库。 */
    public String create(long userId, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMillis))
                .signWith(key)
                .compact();
    }

    /**
     * 解析。签名不对 / 过期 / 格式坏都会抛 {@code JwtException}，
     * 由调用方统一转成 401——**不要**在这里返回 null 让调用方逐处判空，
     * 那种写法漏一处就是「解析失败但放行」。
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
