package com.demo01.business.common;

/** 未登录 / token 失效 → HTTP 401。前端拦截器据此清登录态并跳登录页。 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
