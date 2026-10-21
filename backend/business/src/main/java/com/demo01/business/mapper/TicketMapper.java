package com.demo01.business.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demo01.business.entity.Ticket;
import org.apache.ibatis.annotations.Mapper;

/** `Ticket` 的数据访问层。BaseMapper 已覆盖本项目的单表增删改查（选型②）。 */
@Mapper
public interface TicketMapper extends BaseMapper<Ticket> {
}
