package com.demo01.business.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis 版 token 白名单（决策「单点登录采用 JWT + Redis」的正式实现）。
 *
 * <p>启用方式：{@code demo01.auth.token-store=redis}，连接地址取
 * {@code spring.data.redis.*}（默认 {@code 127.0.0.1:6379}）。
 *
 * <p>键前缀 {@code demo01:token:} 是给「一个 Redis 放多个应用」留的隔离。
 * TTL 直接交给 Redis 过期，不在应用侧维护时间戳——服务重启、多实例都不会
 * 把已登出的 token 复活。
 */
@Component
@ConditionalOnProperty(name = "demo01.auth.token-store", havingValue = "redis")
public class RedisTokenStore implements TokenStore {

    private static final String PREFIX = "demo01:token:";

    private final StringRedisTemplate redis;

    public RedisTokenStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void put(String token, long ttlMillis) {
        redis.opsForValue().set(PREFIX + token, "1", ttlMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean contains(String token) {
        return Boolean.TRUE.equals(redis.hasKey(PREFIX + token));
    }

    @Override
    public void remove(String token) {
        redis.delete(PREFIX + token);
    }
}
