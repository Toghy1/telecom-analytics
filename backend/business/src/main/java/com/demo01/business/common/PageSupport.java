package com.demo01.business.common;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.function.Function;

/**
 * 分页结果的「越界页码收敛」。
 *
 * <p>⚠️ MyBatis-Plus 与前端假数据阶段的分页器（{@code api/mock/paginate.js}）
 * 在这一点上**不一样**：
 * <ul>
 *   <li>假分页把 {@code current} 夹到 {@code [1, pages]}，越界请求拿到的是最后一页；</li>
 *   <li>MyBatis-Plus **不夹**：请求第 4 页而实际只有 3 页时，它照实返回
 *       {@code current=4, records=[]}。</li>
 * </ul>
 * 管理后台三页在「删掉当前页最后一条」之后都会重拉当前页，正好会踩到越界：
 * 页面会变成一张空表，而页码显示 4/3。前端那些页面是照假分页的口径写的
 * （注释里明说「后端会把越界页码收敛到有效范围」），所以这里补上这一次收敛，
 * 而不是去改三个页面。
 *
 * @param first   第一次查询结果
 * @param refetch 按页码重查（只在越界时调用一次）
 */
public final class PageSupport {

    private PageSupport() {
    }

    public static <T> PageResult<T> of(Page<T> first, Function<Long, Page<T>> refetch) {
        boolean outOfRange = first.getRecords().isEmpty()
                && first.getCurrent() > 1
                && first.getPages() > 0
                && first.getCurrent() > first.getPages();
        if (outOfRange) {
            return PageResult.of(refetch.apply(first.getPages()));
        }
        return PageResult.of(first);
    }
}
