package com.demo01.business.common;

/**
 * 统一响应体（决策「统一响应体采用 Result&lt;T&gt;」）。
 *
 * ⚠️ 与 `analysis-service` 里的同名类**刻意各留一份**：两个应用是各自独立
 *    构建、独立部署的 jar（决策「工程划分为双应用」），将来业务应用上
 *    MyBatis-Plus、分析服务保持 JdbcTemplate，依赖树必然分叉。共用一个
 *    common 模块会把两个应用的依赖锁在一起，为了省这二十行不值得。
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
