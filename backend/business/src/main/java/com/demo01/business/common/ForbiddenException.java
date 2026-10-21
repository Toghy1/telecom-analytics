package com.demo01.business.common;

/** 已登录但角色不符 → HTTP 403。 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
