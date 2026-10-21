package com.demo01.business.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.demo01.business.auth.LoginContext;
import com.demo01.business.common.BizException;
import com.demo01.business.common.MaskUtil;
import com.demo01.business.common.PageQuery;
import com.demo01.business.common.PageResult;
import com.demo01.business.common.PageSupport;
import com.demo01.business.entity.Account;
import com.demo01.business.entity.Ticket;
import com.demo01.business.mapper.AccountMapper;
import com.demo01.business.mapper.TicketMapper;
import com.demo01.business.vo.AdminTicketVO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 管理后台 · 工单管理（《开发文档》§6.6）。
 *
 * <p>⚠️ 本类的列表**不按 {@code user_id} 过滤**，与用户端的 {@code TicketService.list()}
 * 恰好相反。复用那个方法的话，管理员列表里只会出现自己的工单，且不报错。
 *
 * <p>⚠️ **状态机是项目两处真实业务逻辑之一**（另一处是套餐办理）。《开发文档》
 * 明确要求「状态迁移的合法性要在后端校验，不可从简到只剩一个下拉框改值」。
 * 本类把这条要求落成两层：
 * <ol>
 *   <li>{@link #advance} 先按 {@link #TRANSITION} 查当前状态的唯一合法后继；</li>
 *   <li>落库用 {@code UPDATE … WHERE id = ? AND status = 当前状态} 的条件更新，
 *       受影响的不是 1 行就拒绝——两个管理员同时点同一张工单时，
 *       后到的那个会拿到「请刷新后重试」，而不是把状态改飞。</li>
 * </ol>
 */
@Service
public class AdminTicketService {

    /**
     * 状态机的合法迁移表。**状态机只有这一处定义**——前端 {@code Tickets.vue}
     * 的按钮映射是展示层，不是第二份真相。
     *
     * <p>{@code DONE} 刻意不出现在表里：查不到后继就是「已办结」。
     */
    private static final Map<String, String> TRANSITION = Map.of(
            "PENDING", "PROCESSING",
            "PROCESSING", "DONE");

    private static final String DONE = "DONE";

    private final TicketMapper ticketMapper;
    private final AccountMapper accountMapper;

    public AdminTicketService(TicketMapper ticketMapper, AccountMapper accountMapper) {
        this.ticketMapper = ticketMapper;
        this.accountMapper = accountMapper;
    }

    /**
     * 全部工单（分页 + 筛选），按提交时间倒序。
     *
     * @param type    NETWORK / BILLING / CONSULT / COMPLAINT；空 = 全部
     * @param status  PENDING / PROCESSING / DONE；空 = 全部
     * @param keyword 匹配工单号（含片段）或描述正文
     */
    public PageResult<AdminTicketVO> list(int page, int size, String type,
                                          String status, String keyword) {
        String k = trim(keyword);
        LambdaQueryWrapper<Ticket> wrapper = Wrappers.<Ticket>lambdaQuery()
                .eq(!trim(type).isEmpty(), Ticket::getType, trim(type))
                .eq(!trim(status).isEmpty(), Ticket::getStatus, trim(status))
                // 工单号是数字列，转成字符串再 LIKE，才能支持「只输后几位」。
                // 用 {0} 占位符传参而不是拼字符串：后者要自己防注入。
                .and(!k.isEmpty(), q -> q
                        .apply("CAST(id AS CHAR) LIKE CONCAT('%', {0}, '%')", k)
                        .or().like(Ticket::getContent, k))
                // 倒序 + id 兜底：同一秒提交的两张单也要有稳定顺序，否则翻页会飘。
                .orderByDesc(Ticket::getCreateTime)
                .orderByDesc(Ticket::getId);

        Page<Ticket> first = ticketMapper.selectPage(PageQuery.of(page, size), wrapper);
        PageResult<Ticket> result = PageSupport.of(first,
                n -> ticketMapper.selectPage(PageQuery.of(n.intValue(), size), wrapper));

        Map<Long, Account> accounts = loadAccounts(result.records());
        List<AdminTicketVO> rows = result.records().stream()
                .map(t -> AdminTicketVO.from(t, userLoginName(t, accounts), handlerName(t, accounts)))
                .toList();
        return PageResult.of(rows, result.total(), result.pages(), result.current());
    }

    /**
     * 推进工单状态：{@code PENDING → PROCESSING → DONE}，{@code DONE} 是终态。
     *
     * <p>⚠️ {@code handler_id} / {@code handle_time} 与 {@code status} 必须**同步**更新：
     * <ul>
     *   <li>开始处理：写入处理人（当前登录管理员），{@code handle_time} 保持 null
     *       ——它是**办结**时间，不是开始处理时间；</li>
     *   <li>标记完成：处理人已有就不改（接手人只在第一次落库），写入当前时间。</li>
     * </ul>
     */
    public AdminTicketVO advance(Long id, String targetStatus) {
        Ticket ticket = id == null ? null : ticketMapper.selectById(id);
        if (ticket == null) {
            throw new BizException("工单不存在");
        }

        String current = ticket.getStatus();
        String allowed = TRANSITION.get(current);
        if (allowed == null) {
            throw new BizException("该工单已办结，不能再次流转");
        }
        if (!allowed.equals(trim(targetStatus))) {
            throw new BizException("不允许的状态流转，请刷新后重试");
        }

        boolean done = DONE.equals(allowed);
        LocalDateTime handleTime = done ? LocalDateTime.now() : null;
        // ⚠️ 这里**不做**「处理时间不能早于提交时间」的校验。
        //    假数据阶段有这一条，但真后端两个时间都取自同一台机器的时钟，
        //    业务上不可能倒序；而 `create_time` 列是 DATETIME(0)，MySQL 会把
        //    插入时的毫秒**四舍五入**到下一秒——「提交后 0.2 秒就点完成」时，
        //    读回来的 create_time 反而比 now 大，校验会误杀一次合法操作。
        // 已有人接手过的工单，流转时不改处理人（`??` 而非赋值）。
        Long handlerId = ticket.getHandlerId() != null
                ? ticket.getHandlerId()
                : LoginContext.userId();

        // ⚠️ 条件里必须带 `status = current`：这是「两个标签页同时操作同一张单」
        //    的最终防线。受影响行数不是 1，说明在读与写之间状态已经被别人改了。
        int affected = ticketMapper.update(null, Wrappers.<Ticket>lambdaUpdate()
                .eq(Ticket::getId, id)
                .eq(Ticket::getStatus, current)
                .set(Ticket::getStatus, allowed)
                .set(Ticket::getHandlerId, handlerId)
                .set(Ticket::getHandleTime, handleTime));
        if (affected == 0) {
            throw new BizException("不允许的状态流转，请刷新后重试");
        }

        ticket.setStatus(allowed);
        ticket.setHandlerId(handlerId);
        ticket.setHandleTime(handleTime);
        Map<Long, Account> accounts = loadAccounts(List.of(ticket));
        return AdminTicketVO.from(ticket, userLoginName(ticket, accounts), handlerName(ticket, accounts));
    }

    /* ------------------------------------------------------------------ */

    /** 一次查齐本页涉及的提交人与处理人，避免每行两次单查。 */
    private Map<Long, Account> loadAccounts(List<Ticket> tickets) {
        List<Long> ids = tickets.stream()
                .flatMap(t -> Stream.of(t.getUserId(), t.getHandlerId()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return accountMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Account::getId, a -> a));
    }

    /** 提交人显示名：**脱敏**（管理员也只能看到脱敏值）。 */
    private static String userLoginName(Ticket ticket, Map<Long, Account> accounts) {
        Account user = accounts.get(ticket.getUserId());
        return user == null ? "未知用户" : MaskUtil.loginName(user.getLoginName());
    }

    /** 处理人显示名；未处理（{@code handler_id = null}）返回 null，前端显示 '--'。 */
    private static String handlerName(Ticket ticket, Map<Long, Account> accounts) {
        if (ticket.getHandlerId() == null) {
            return null;
        }
        Account handler = accounts.get(ticket.getHandlerId());
        return handler == null ? "未知处理人" : MaskUtil.loginName(handler.getLoginName());
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
