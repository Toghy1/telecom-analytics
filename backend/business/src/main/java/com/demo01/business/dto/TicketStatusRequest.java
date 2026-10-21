package com.demo01.business.dto;

import lombok.Data;

/**
 * 工单状态流转入参：{@code {status}}。
 *
 * <p>⚠️ 只收「目标状态」，不收处理人 / 处理时间——后两者由服务端从 token 与
 * 当前时钟得出。让前端传 {@code handler_id} 等于允许任何人把工单记到别人头上。
 */
@Data
public class TicketStatusRequest {

    /** PROCESSING / DONE（PENDING 只能由用户提交时产生，不可流转回去）。 */
    private String status;
}
