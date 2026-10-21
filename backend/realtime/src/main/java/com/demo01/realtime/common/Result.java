package com.demo01.realtime.common;

/**
 * 统一响应体（决策「统一响应体采用 Result&lt;T&gt;」）。
 *
 * 三个应用各留一份，理由见 `business/common/Result.java` 的类注释：
 * 它们是各自独立构建的 jar，不共享 common 模块。
 */
public record Result<T>(int code, String message, T data) {

    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "操作成功", data);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }
}
