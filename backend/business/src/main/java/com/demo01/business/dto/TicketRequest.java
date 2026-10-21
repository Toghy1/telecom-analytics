package com.demo01.business.dto;

import lombok.Data;

/**
 * 提交工单入参：{@code {type, content}}。
 *
 * <p>⚠️ **没有 status / handler_id / handle_time 三个字段**，这是刻意的：
 * 新工单的状态恒为 {@code PENDING}，由 service 写死。让入参带上 status
 * 等于把状态机的入口敞开，谁都能塞一个「已完成」进来。
 *
 * <p>类型与字数的校验放在 service（类型要对常量表、字数要 trim 后再算），
 * 这里不加注解是为了让错误信息与前端假数据阶段**逐字一致**。
 */
@Data
public class TicketRequest {

    private String type;

    private String content;
}
