package com.demo01.business.common;

/**
 * 业务异常（决策「统一响应体采用 Result&lt;T&gt;」的两层失败模型）。
 *
 * <p>这里抛的是**业务层**的错误——「该套餐已下架」「当前已是该套餐」这类，
 * 对用户有意义、页面应该直接把 {@code message} 弹出来。它们走
 * HTTP 200 + {@code code != 200}，不是 HTTP 4xx/5xx。
 *
 * <p>⚠️ 401 / 403 不在这里：那是**程序层**的错误（token 失效、角色不符），
 * 由 {@code UnauthorizedException} / {@code ForbiddenException} 走真正的
 * HTTP 状态码。前端 axios 拦截器按 HTTP 401 统一清登录态、跳登录页，
 * 若塞进 {@code code} 里，那套「写一遍覆盖全部接口」的处理就失效了。
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        this(1001, message);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
