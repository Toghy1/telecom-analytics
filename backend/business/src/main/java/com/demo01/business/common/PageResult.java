package com.demo01.business.common;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

/**
 * 分页响应体，字段与 MyBatis-Plus 的 {@code Page<T>} 逐一对应
 * （决策「前端 REST 分页口径定稿」）。
 *
 * <p>⚠️ 字段名**一个都不能改**：前端假数据阶段用的 {@code api/mock/paginate.js}
 * 就是照这个形状写的，页面组件按 {@code total / pages / current / records}
 * 解构。改一个名字，接线时每个列表页都要跟着改——而「接线时页面一行不改」
 * 正是当初把假分页做成同形的全部意义。
 *
 * @param total   符合条件的总条数
 * @param pages   总页数
 * @param current 当前页（从 1 起）
 * @param records 当前页的数据
 */
public record PageResult<T>(long total, long pages, long current, List<T> records) {

    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(page.getTotal(), page.getPages(), page.getCurrent(), page.getRecords());
    }

    /** 已手工分页（不走 Page 查询）的场景，如「按月聚合」后切片。 */
    public static <T> PageResult<T> of(List<T> records, long total, long pages, long current) {
        return new PageResult<>(total, pages, current, records);
    }
}
