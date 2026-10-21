package com.demo01.business.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demo01.business.entity.Promo;
import org.apache.ibatis.annotations.Mapper;

/** `Promo` 的数据访问层。BaseMapper 已覆盖本项目的单表增删改查（选型②）。 */
@Mapper
public interface PromoMapper extends BaseMapper<Promo> {
}
