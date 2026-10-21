package com.demo01.business.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * `t_ticket` 工单表（《开发文档》§5.2 ⑥）。
 *
 * <p>⚠️ 状态机 {@code PENDING → PROCESSING → DONE}，**迁移由管理后台驱动**，
 * 用户端只读。新单的 {@code status} 由服务端写死为 PENDING，
 * 绝不从请求体里取——否则谁都能塞一个「已完成」进来。
 */
@Data
@TableName("t_ticket")
public class Ticket {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** NETWORK / BILLING / CONSULT / COMPLAINT（决策「工单类型定稿为四类」）。 */
    private String type;

    private String content;

    /** PENDING / PROCESSING / DONE */
    private String status;

    /** 处理人（管理员账号 id），未处理为 null。 */
    private Long handlerId;

    private LocalDateTime createTime;

    /** 未处理为 null。 */
    private LocalDateTime handleTime;
}
