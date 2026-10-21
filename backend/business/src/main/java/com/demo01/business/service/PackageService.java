package com.demo01.business.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.demo01.business.entity.Package;
import com.demo01.business.mapper.PackageMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 套餐查询（《开发文档》§6.4 #3）。
 *
 * <p>⚠️ 两个方法过滤 {@code status} 的口径**刻意不同**：
 * <ul>
 *   <li>{@code list} 只返回 {@code status = 1}（在售）——回答「现在能办哪些」；</li>
 *   <li>{@code getById} **不看 status**——回答「这个 id 是哪个套餐」。
 *       用户已订购的套餐若已下架，账户总览仍应显示它的名字。</li>
 * </ul>
 * 拿一个去顶另一个不会报错，只会让某处少显示一个套餐名。
 */
@Service
public class PackageService {

    private final PackageMapper packageMapper;

    public PackageService(PackageMapper packageMapper) {
        this.packageMapper = packageMapper;
    }

    /**
     * 在售套餐，按月费升序。
     *
     * <p>⚠️ 升序还有一个验收用途：停用的「学生特惠 29 元套餐」在升序里排第 2
     * （19 / 29停用 / 39 / 79 / 129），一旦状态过滤失效，它立刻挤进首页前 3 条，
     * 一眼可见。若它排在末尾，这条过滤写没写对，看首页都是同一个样子。
     */
    public List<Package> list(Integer limit) {
        List<Package> rows = packageMapper.selectList(Wrappers.<Package>lambdaQuery()
                .eq(Package::getStatus, 1)
                .orderByAsc(Package::getMonthlyFee));

        // 「首页只展示几个」这个数字留在调用方（前端传 limit），这里只管截断。
        if (limit != null && limit > 0 && rows.size() > limit) {
            return rows.subList(0, limit);
        }
        return rows;
    }

    /** 按 id 查单个套餐，**不过滤 status**；查不到返回 null（前端按 null 处理）。 */
    public Package getById(Long id) {
        return id == null ? null : packageMapper.selectById(id);
    }
}
