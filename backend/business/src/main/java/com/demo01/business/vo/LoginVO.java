package com.demo01.business.vo;

/**
 * 登录成功出参：{@code { token, account }}。
 *
 * <p>形状与前端假数据阶段 {@code api/auth.js#login} 的返回**完全一致**，
 * 所以 `stores/auth.js` 一行都不用改。
 */
public record LoginVO(String token, AccountVO account) {
}
