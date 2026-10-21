package com.demo01.business.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.demo01.business.entity.Package;
import com.demo01.business.entity.Promo;
import com.demo01.business.mapper.PackageMapper;
import com.demo01.business.mapper.PromoMapper;
import com.demo01.business.vo.PromoVO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 促销活动查询（《开发文档》§6.4 #2）。
 */
@Service
public class PromoService {

    private final PromoMapper promoMapper;
    private final PackageMapper packageMapper;

    public PromoService(PromoMapper promoMapper, PackageMapper packageMapper) {
        this.promoMapper = promoMapper;
        this.packageMapper = packageMapper;
    }

    /**
     * 进行中的活动，按档期倒序。
     *
     * <p>⚠️ 「进行中」是**两个条件**，缺一不可：
     * <ol>
     *   <li>{@code status = 1}——运营挂没挂出来；</li>
     *   <li>{@code start_time <= 现在 <= end_time}——活动自己的档期。</li>
     * </ol>
     * 只看第一个是不够的：活动到期后忘了置 0 是运营后台最常见的疏漏。
     * 两者打架时以日期为准。种子里有一条 {@code status = 1 但档期已过} 的，
     * 专门用来证伪「只过滤 status」这种写法。
     *
     * <p>⚠️ 时间比较放在 SQL 里（{@code LocalDateTime} 直接下推），不在 Java 里
     * 先查全表再过滤——将来活动多了，那是一次把整表捞上来的查询。
     */
    public List<PromoVO> list(Integer limit) {
        LocalDateTime now = LocalDateTime.now();

        List<Promo> rows = promoMapper.selectList(Wrappers.<Promo>lambdaQuery()
                .eq(Promo::getStatus, 1)
                .le(Promo::getStartTime, now)
                .ge(Promo::getEndTime, now)
                .orderByDesc(Promo::getStartTime));

        if (limit != null && limit > 0 && rows.size() > limit) {
            rows = rows.subList(0, limit);
        }
        if (rows.isEmpty()) {
            return List.of();
        }

        // 套餐名**一次批量查出来**，不是逐条查（N+1），也不是让前端拿到 id 再查。
        // 这里用 selectBatchIds 而不是 SQL JOIN：JOIN 的结果要映射进一个
        // 带 package_name 的普通类，多一个只为一条 SQL 存在的 DTO，不值。
        List<Long> packageIds = rows.stream()
                .map(Promo::getPackageId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> names = packageIds.isEmpty()
                ? Map.of()
                : packageMapper.selectBatchIds(packageIds).stream()
                        .collect(Collectors.toMap(Package::getId, Package::getName));

        return rows.stream()
                .map(p -> new PromoVO(p.getId(), p.getTitle(), p.getContent(), p.getPackageId(),
                        names.getOrDefault(p.getPackageId(), ""),
                        p.getStartTime(), p.getEndTime(), p.getStatus()))
                .toList();
    }
}
