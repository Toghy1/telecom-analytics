package com.demo01.business.vo;

import com.demo01.business.entity.Ticket;

import java.time.LocalDateTime;

/**
 * 管理后台工单列表出参。
 *
 * <p>比用户端的 {@code Ticket} 实体多两个「拼好的名字」：
 * <ul>
 *   <li>{@code userLoginName}：提交人（**脱敏**，管理员也只能看到脱敏值）；</li>
 *   <li>{@code handlerName}：处理人，未处理时为 {@code null}（前端显示 {@code '--'}）。</li>
 * </ul>
 * 两个名字都在 service 里按 id 批量查 {@code t_account} 拼好——前端假数据阶段
 * 有一张单独的「处理人参照表」，是因为 mock 里历史工单的 handler_id 不在账号表里；
 * 真库里 {@code t_ticket.handler_id} 就是 {@code t_account.id}，直接查得到。
 */
public record AdminTicketVO(
        Long id,
        Long userId,
        String userLoginName,
        String type,
        String content,
        String status,
        Long handlerId,
        String handlerName,
        LocalDateTime createTime,
        LocalDateTime handleTime) {

    public static AdminTicketVO from(Ticket ticket, String userLoginName, String handlerName) {
        return new AdminTicketVO(
                ticket.getId(),
                ticket.getUserId(),
                userLoginName,
                ticket.getType(),
                ticket.getContent(),
                ticket.getStatus(),
                ticket.getHandlerId(),
                handlerName,
                ticket.getCreateTime(),
                ticket.getHandleTime());
    }
}
