package com.demo01.business.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.demo01.business.common.BizException;
import com.demo01.business.common.PageQuery;
import com.demo01.business.common.PageResult;
import com.demo01.business.dto.TicketRequest;
import com.demo01.business.entity.Ticket;
import com.demo01.business.mapper.TicketMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 工单提交与查询（《开发文档》§6.4 #6）。
 *
 * <p>⚠️ 状态只有两个入口：新单恒为 {@code PENDING}，流转由管理后台驱动。
 * 本类**没有任何方法接受调用方传来的 status**——那是刻意的。
 */
@Service
public class TicketService {

    /** 与前端 {@code utils/constants.js#TICKET_TYPE} 的键一一对应。 */
    private static final Set<String> TYPES = Set.of("NETWORK", "BILLING", "CONSULT", "COMPLAINT");

    /** 与前端 {@code TICKET_CONTENT_LIMIT} 同口径；表单 maxlength 只管界面，接口自己也要拦。 */
    private static final int CONTENT_MIN = 5;
    private static final int CONTENT_MAX = 200;

    private static final String STATUS_PENDING = "PENDING";

    private final TicketMapper ticketMapper;

    public TicketService(TicketMapper ticketMapper) {
        this.ticketMapper = ticketMapper;
    }

    /** 我的工单（分页），按提交时间倒序。 */
    public PageResult<Ticket> list(long userId, int page, int size) {
        Page<Ticket> p = ticketMapper.selectPage(PageQuery.of(page, size),
                Wrappers.<Ticket>lambdaQuery()
                        .eq(Ticket::getUserId, userId)
                        .orderByDesc(Ticket::getCreateTime));
        return PageResult.of(p);
    }

    /**
     * 提交工单。
     *
     * <p>错误消息与前端假数据阶段**逐字一致**——接线前后用户看到的提示不该变。
     */
    public Ticket submit(long userId, TicketRequest request) {
        String type = request.getType() == null ? "" : request.getType().trim();
        if (!TYPES.contains(type)) {
            throw new BizException("请选择报障类型");
        }

        // 先 trim 再算长度：全是空格的「描述」不算描述。
        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (content.length() < CONTENT_MIN) {
            throw new BizException("问题描述至少 " + CONTENT_MIN + " 个字");
        }
        if (content.length() > CONTENT_MAX) {
            throw new BizException("问题描述不能超过 " + CONTENT_MAX + " 个字");
        }

        Ticket ticket = new Ticket();
        ticket.setUserId(userId);
        ticket.setType(type);
        ticket.setContent(content);
        // 状态、经办人、处理时间全部在这里定死，不接受入参。
        ticket.setStatus(STATUS_PENDING);
        ticket.setHandlerId(null);
        ticket.setHandleTime(null);
        ticket.setCreateTime(LocalDateTime.now());
        ticketMapper.insert(ticket);

        // insert 后 MyBatis-Plus 会把自增主键回填到 ticket.id，直接返回即可。
        return ticket;
    }
}
