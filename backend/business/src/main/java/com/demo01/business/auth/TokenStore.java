package com.demo01.business.auth;

/**
 * token 白名单（决策「单点登录采用 JWT + Redis」）。
 *
 * <p>为什么 JWT 还要白名单：JWT 是无状态的，签发后**在过期前永远有效**——
 * 用户点了「退出登录」，服务端只有把 token 记进黑名单（或反过来只认白名单里
 * 的 token）才能真正让它失效，否则旧 token 照样能调接口。
 *
 * <p>两套实现，由 {@code demo01.auth.token-store} 选择：
 * <ul>
 *   <li>{@link MemoryTokenStore}（默认）：进程内 Map，本地开发期用，
 *       重启即全员掉线、多实例各自为政；</li>
 *   <li>{@link RedisTokenStore}：VM 部署用，多实例共享、重启不丢。</li>
 * </ul>
 * 接口形状两套一致，注入方（{@code AuthService} / {@code AuthInterceptor}）
 * 不知道也不关心背后是哪个。
 */
public interface TokenStore {

    /** 登记 token，{@code ttlMillis} 毫秒后自动失效。 */
    void put(String token, long ttlMillis);

    boolean contains(String token);

    void remove(String token);
}
