package com.demo01.business.auth;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * token 白名单（决策「单点登录采用 JWT + Redis」）。**本地开发期的替身实现**。
 *
 * <p>为什么 JWT 还要白名单：JWT 是无状态的，签发后**在过期前永远有效**——
 * 用户点了「退出登录」，服务端只有把 token 记进黑名单（或反过来只认白名单里
 * 的 token）才能真正让它失效，否则旧 token 照样能调接口。
 *
 * <p>⚠️ **本实现是进程内 Map，不是 Redis。** 取舍如实记录：
 * <ul>
 *   <li>本地 Windows 没装 Redis（见决策「开发期采用本地 Windows 最小闭环」），
 *       而上一条决策要求的「统一登出、踢人下线、在线用户数」都需要它；</li>
 *   <li>接口形状按 Redis 那套写（`put / contains / remove`，带过期时间），
 *       上 VM 部署时只换这一个类，**TokenStore 的调用方一行不用改**；</li>
 *   <li>代价：重启服务即全员掉线；多实例部署时各自为政。这两条在单机开发期
 *       都不构成问题，进 VM 后必须换成 Redis。</li>
 * </ul>
 */
@Component
public class TokenStore {

    /** token → 过期时刻（epoch 毫秒）。 */
    private final Map<String, Long> tokens = new ConcurrentHashMap<>();

    public void put(String token, long ttlMillis) {
        tokens.put(token, System.currentTimeMillis() + ttlMillis);
    }

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

    public void remove(String token) {
        tokens.remove(token);
    }
}
