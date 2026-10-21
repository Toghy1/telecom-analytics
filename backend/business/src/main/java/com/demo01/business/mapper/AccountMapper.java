package com.demo01.business.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demo01.business.entity.Account;
import org.apache.ibatis.annotations.Mapper;

/** `Account` 的数据访问层。BaseMapper 已覆盖本项目的单表增删改查（选型②）。 */
@Mapper
public interface AccountMapper extends BaseMapper<Account> {
}
