package com.demo01.analysis.common;

/**
 * 统一响应体（决策「统一响应体采用 Result&lt;T&gt;」）。
 *
 * 前端 axios 拦截器按 `code === 200` 解包 `data`，其余按业务错误处理。
 */
public record Result<T>(int code, String message, T data) {

    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "操作成功", data);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }
}
