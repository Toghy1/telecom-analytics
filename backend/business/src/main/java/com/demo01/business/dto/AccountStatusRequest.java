package com.demo01.business.dto;

import lombok.Data;

/** 启用 / 停用账号入参：{@code {status}}，1 启用 / 0 停用。 */
@Data
public class AccountStatusRequest {

    /**
     * ⚠️ 用包装类型而不是 {@code int}：不传时 Spring 给的是 {@code null}，
     * service 能报「状态取值非法」；用 {@code int} 的话不传就是 0，
     * 会把一次漏传当成「停用」执行——不报错，后果却是账号登不上。
     */
    private Integer status;
}
