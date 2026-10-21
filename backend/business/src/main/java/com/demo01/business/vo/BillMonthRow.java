package com.demo01.business.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 「按月列表」的一行：{@code WHERE type='BILL' GROUP BY bill_month} 的聚合结果。
 *
 * <p>⚠️ 用普通类（默认构造 + setter）而不是 record：这是 MyBatis 的**查询结果**
 * 类型，走的是 setter 映射；record 需要 `argNameBasedConstructorAutoMapping`
 * 之类的额外开关，本项目不值得为一个 DTO 去改全局配置。
 */
@Data
public class BillMonthRow {

    /** 账期 `2026-09`，定长字符串，字典序即时间序。 */
    private String billMonth;

    /** 该账期消费合计（元）。SUM(DECIMAL) 不会有浮点尾巴。 */
    private BigDecimal amount;

    /**
     * 该账期内的消费笔数。
     *
     * <p>⚠️ 字段名必须是 {@code count}：前端 {@code api/bill.js#listBillMonths}
     * 的假数据返回的就是 {@code {bill_month, amount, count}}，页面按
     * {@code row.count} 渲染。改名等于接线时页面要跟着改。
     */
    private Integer count;
}
