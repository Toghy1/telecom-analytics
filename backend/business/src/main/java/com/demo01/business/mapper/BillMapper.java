package com.demo01.business.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demo01.business.entity.Bill;
import com.demo01.business.vo.BillMonthRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * `Bill` 的数据访问层。
 *
 * <p>「按月列表」是 {@code GROUP BY} 聚合，BaseMapper 的单表方法表达不了，
 * 所以额外补一条 {@code @Select}。这是本项目少有的手写 SQL，只此一处。
 */
@Mapper
public interface BillMapper extends BaseMapper<Bill> {

    /**
     * 账期聚合：《开发文档》§5.2 ④ 的 {@code type='BILL' GROUP BY bill_month}。
     *
     * <p>⚠️ {@code user_id} 条件**必须**有。少这一条不会报错，只是把全库用户的
     * 账单合在一起返回——演示时看着「数字更大了」，其实错得离谱。
     *
     * <p>⚠️ `LEFT JOIN t_package` 之类的关联一概没有：本查询只出现 t_bill 一张表，
     * 账期里不需要套餐名。
     *
     * <p>{@code ORDER BY bill_month DESC} 依赖 {@code bill_month} 是定宽的
     * `YYYY-MM`——字典序即时间序，不必转日期。
     */
    @Select("""
            SELECT bill_month        AS billMonth,
                   SUM(amount)       AS amount,
                   COUNT(*)          AS `count`
              FROM t_bill
             WHERE user_id = #{userId}
               AND type = 'BILL'
             GROUP BY bill_month
             ORDER BY bill_month DESC
            """)
    List<BillMonthRow> selectMonths(@Param("userId") long userId);
}
