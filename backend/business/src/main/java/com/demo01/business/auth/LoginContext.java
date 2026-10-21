package com.demo01.business.auth;

/**
 * 当前请求的登录用户（ThreadLocal）。
 *
 * <p>⚠️ 存在的意义是**「当前用户」只能从 token 来，不能从请求参数来**。
 * 业务接口（账单 / 余量 / 工单）的查询条件里都有 {@code user_id}，
 * 若让它作为入参，任何人都能把它改成别人的 id 去查别人的账单——
 * 这类越权在页面上完全看不出来（页面本来只传自己的 id）。
 * 收口到这里之后，Controller 里拿到的 userId 来自签名过的 token。
 *
 * <p>⚠️ **必须在 afterCompletion 里 clear()**：Tomcat 线程是复用的，
 * 不清就会让下一个请求读到上一个用户的身份——而它不报错，只是查错人。
 */
public final class LoginContext {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private LoginContext() {
    }

    public record CurrentUser(long id, String role) {
    }

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    /** 取用户 id；没登录说明拦截器漏放行了，直接抛而不是返回 null。 */
    public static long userId() {
        CurrentUser user = HOLDER.get();
        if (user == null) {
            throw new IllegalStateException("当前请求没有登录用户，拦截器白名单配错了");
        }
        return user.id();
    }

    public static String role() {
        CurrentUser user = HOLDER.get();
        return user == null ? null : user.role();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
