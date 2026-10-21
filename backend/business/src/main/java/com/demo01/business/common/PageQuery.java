package com.demo01.business.common;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 分页入参的收口与夹取。
 *
 * <p>⚠️ 为什么要夹：{@code page=0} 会让 MyBatis-Plus 的 offset 变负（SQL 直接报错），
 * {@code size=1000000} 则是一次能把库拖垮的查询。前端传的是正常值，但接口
 * 不是只有前端会调——浏览器地址栏改个 {@code ?size=999999} 就能试。
 *
 * <p>上限取 50：本项目最大的列表页一页 10 条，50 已经给足余量。
 */
public final class PageQuery {

    public static final int MAX_SIZE = 50;

    private PageQuery() {
    }

    public static <T> Page<T> of(int page, int size) {
        return new Page<>(Math.max(1, page), Math.min(Math.max(1, size), MAX_SIZE));
    }

    /** 只要前 N 条、不需要总数的场景（如「最近 3 条账单」）。 */
    public static <T> Page<T> first(int limit) {
        Page<T> p = new Page<>(1, Math.min(Math.max(1, limit), MAX_SIZE));
        // 不查 count：这个场景的 total 没人用，白白多一条 COUNT(*)。
        p.setSearchCount(false);
        return p;
    }
}
