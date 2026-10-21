package com.demo01.business.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理（选型④「@RestControllerAdvice 全局异常处理」）。
 *
 * <p>⚠️ 两条分支的**出口形状不同**，这是刻意的：
 * <ul>
 *   <li>业务异常 → HTTP 200 + {@code code=1001}。用户是「登录着、只是操作不合法」，
 *       页面要能把 message 直接展示出来。</li>
 *   <li>程序异常（401 / 403 / 500）→ **真正的 HTTP 状态码**。前端 axios 拦截器
 *       按 401 统一清登录态、跳登录页，这套「写一遍覆盖全部接口」依赖的就是
 *       状态码本身，塞进 {@code code} 里就失效了。</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Result<Void>> handleUnauthorized(UnauthorizedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Result.fail(401, e.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Result<Void>> handleForbidden(ForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Result.fail(403, e.getMessage()));
    }

    /** 参数校验失败。取第一条错误即可——表单一次只该提示一个问题。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        FieldError first = e.getBindingResult().getFieldError();
        return Result.fail(1001, first == null ? "参数不合法" : first.getDefaultMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleOther(Exception e) {
        // 未预期的异常必须留栈：日志里没有的话，线上只能看到「服务器开小差了」
        log.error("未处理异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(500, "服务器开小差了，请稍后再试"));
    }
}
