package com.demo01.business.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内 token 白名单，**本地开发期的替身实现**（决策「认证白名单暂以内存实现代替 Redis」）。
 *
 * <p>没配 {@code demo01.auth.token-store} 时默认走这个，本地 Windows 不必装 Redis。
 * 代价如实记录：重启服务即全员掉线；多实例部署时各自为政。上 VM 请设
 * {@code demo01.auth.token-store=redis} 切到 {@link RedisTokenStore}。
 */
@Component
@ConditionalOnProperty(name = "demo01.auth.token-store", havingValue = "memory", matchIfMissing = true)
public class MemoryTokenStore implements TokenStore {

    /** token → 过期时刻（epoch 毫秒）。 */
    private final Map<String, Long> tokens = new ConcurrentHashMap<>();

    @Override
    public void put(String token, long ttlMillis) {
        tokens.put(token, System.currentTimeMillis() + ttlMillis);
    }

    @Override
    public boolean contains(String token) {
        Long expireAt = tokens.get(token);
        if (expireAt == null) {
            return false;
        }
        if (expireAt < System.currentTimeMillis()) {
            // 顺手清理：JWT 自己也会判过期，但 Map 不清理就会一直涨
            tokens.remove(token);
            return false;
        }
        return true;
    }

    @Override
    public void remove(String token) {
        tokens.remove(token);
    }
}
