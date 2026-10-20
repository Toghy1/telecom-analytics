package com.demo01.analysis.common;

import java.util.List;

/**
 * 分页返回体，字段与 MyBatis-Plus 的 `Page&lt;T&gt;` 逐一对应
 * （决策「前端 REST 分页口径定稿」）。前端假分页 `paginate()` 也是这个形状，
 * 所以接线时页面组件一行不改。
 */
public record PageResult<T>(long total, int pages, int current, List<T> records) {
}
