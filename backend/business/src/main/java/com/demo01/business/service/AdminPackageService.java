package com.demo01.business.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.demo01.business.common.BizException;
import com.demo01.business.common.PageQuery;
import com.demo01.business.common.PageResult;
import com.demo01.business.common.PageSupport;
import com.demo01.business.dto.AdminPackageRequest;
import com.demo01.business.entity.Account;
import com.demo01.business.entity.OrderRecord;
import com.demo01.business.entity.Package;
import com.demo01.business.mapper.AccountMapper;
import com.demo01.business.mapper.OrderRecordMapper;
import com.demo01.business.mapper.PackageMapper;
import com.demo01.business.vo.AdminPackageVO;
import com.demo01.business.vo.OptionVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理后台 · 套餐配置（《开发文档》§6.6）。
 *
 * <p>⚠️ 与营业厅的 {@code PackageService.list()} **口径相反**：那里只返回
 * {@code status = 1}（回答「现在能办哪些」），这里**不过滤 status**
 * （回答「系统里有哪些」）。图省事复用前者的话，已停售的「学生特惠 29 元套餐」
 * 会从管理端凭空消失且不报错——而它正是最该被看见的那一条：要下架它，
 * 得先找得到它。
 *
 * <p>⚠️ 「删除」在有引用时**拒绝**，正确的动作是「停售」。理由不是怕麻烦：
 * 删除会让历史办理记录里的 {@code package_id} 指向不存在的套餐，
 * 办理记录页面会显示成「未知套餐」——一个不报错的静默数据错误。
 */
@Service
public class AdminPackageService {

    private final PackageMapper packageMapper;
    private final AccountMapper accountMapper;
    private final OrderRecordMapper orderMapper;

    public AdminPackageService(PackageMapper packageMapper, AccountMapper accountMapper,
                               OrderRecordMapper orderMapper) {
        this.packageMapper = packageMapper;
        this.accountMapper = accountMapper;
        this.orderMapper = orderMapper;
    }

    /**
     * 套餐列表（分页 + 筛选）。
     *
     * @param status 1 在售 / 0 停售；{@code null} = 全部（不能写成真假判断，
     *               {@code 0} 是合法取值）
     */
    public PageResult<AdminPackageVO> list(int page, int size, Integer status, String keyword) {
        String k = trim(keyword);
        LambdaQueryWrapper<Package> wrapper = Wrappers.<Package>lambdaQuery()
                .eq(status != null, Package::getStatus, status)
                .like(!k.isEmpty(), Package::getName, k)
                // 与营业厅同一排序：按月费升序。id 作为次序的最后一道保险，
                // 免得同价套餐在翻页时飘来飘去。
                .orderByAsc(Package::getMonthlyFee)
                .orderByAsc(Package::getId);

        Page<Package> first = packageMapper.selectPage(PageQuery.of(page, size), wrapper);
        PageResult<Package> result = PageSupport.of(first,
                n -> packageMapper.selectPage(PageQuery.of(n.intValue(), size), wrapper));

        List<AdminPackageVO> rows = result.records().stream()
                .map(p -> AdminPackageVO.from(p, userCount(p.getId())))
                .toList();
        return PageResult.of(rows, result.total(), result.pages(), result.current());
    }

    /**
     * 套餐下拉选项，按月费升序，**不过滤 status**。
     *
     * <p>给存量用户指派套餐时，已停售的套餐仍要能选（种子数据里 1006 / 1013
     * 用的就是已停售的学生特惠）。文案里带出「已停售」，让这一项自解释。
     */
    public List<OptionVO> options() {
        return packageMapper.selectList(Wrappers.<Package>lambdaQuery()
                        .orderByAsc(Package::getMonthlyFee)
                        .orderByAsc(Package::getId))
                .stream()
                .map(p -> new OptionVO(p.getId(),
                        p.getName() + "（" + feeText(p.getMonthlyFee()) + " 元"
                                + (Integer.valueOf(1).equals(p.getStatus()) ? "" : " · 已停售") + "）"))
                .toList();
    }

    /** 新增套餐。新建的套餐默认在售，除非表单明确选了停售。 */
    public AdminPackageVO create(AdminPackageRequest request) {
        ValidatedPackage v = validate(request);

        Package pkg = new Package();
        pkg.setName(v.name());
        pkg.setMonthlyFee(v.monthlyFee());
        pkg.setDataQuota(v.dataQuota());
        pkg.setVoiceQuota(v.voiceQuota());
        pkg.setSmsQuota(v.smsQuota());
        pkg.setDescription(v.description());
        pkg.setStatus(v.status());
        pkg.setCreateTime(LocalDateTime.now());
        packageMapper.insert(pkg);

        return AdminPackageVO.from(pkg, 0);
    }

    /**
     * 编辑套餐。
     *
     * <p>⚠️ {@code createTime} **不随编辑改动**：它是「这个套餐什么时候上架的」，
     * 改文案不该改它。改动它还会让「上架时间早于办理时间」的时间线自洽性断掉。
     */
    public AdminPackageVO update(Long id, AdminPackageRequest request) {
        Package pkg = require(id);
        ValidatedPackage v = validate(request);

        pkg.setName(v.name());
        pkg.setMonthlyFee(v.monthlyFee());
        pkg.setDataQuota(v.dataQuota());
        pkg.setVoiceQuota(v.voiceQuota());
        pkg.setSmsQuota(v.smsQuota());
        pkg.setDescription(v.description());
        pkg.setStatus(v.status());
        packageMapper.updateById(pkg);

        return AdminPackageVO.from(pkg, userCount(id));
    }

    /**
     * 删除套餐。
     *
     * <p>⚠️ 有引用就拒绝，提示里带上引用条数 N——与列表的「使用数」列
     * 用同一个 {@link #userCount(Long)}，否则会出现「显示 0 却删不掉」。
     */
    public boolean delete(Long id) {
        require(id);
        long used = userCount(id);
        if (used > 0) {
            throw new BizException("该套餐已被使用（关联 " + used
                    + " 条记录），无法删除；如需对用户下架，请改用「停售」");
        }
        packageMapper.deleteById(id);
        return true;
    }

    /* ------------------------------------------------------------------ */

    /** 某套餐被多少处引用：账号当前套餐 + 历史办理记录。删除守卫与列表共用。 */
    private long userCount(Long packageId) {
        long asCurrent = accountMapper.selectCount(Wrappers.<Account>lambdaQuery()
                .eq(Account::getPackageId, packageId));
        long inOrders = orderMapper.selectCount(Wrappers.<OrderRecord>lambdaQuery()
                .eq(OrderRecord::getPackageId, packageId));
        return asCurrent + inOrders;
    }

    private Package require(Long id) {
        Package pkg = id == null ? null : packageMapper.selectById(id);
        if (pkg == null) {
            throw new BizException("套餐不存在");
        }
        return pkg;
    }

    /**
     * 新增与编辑共用的字段校验。
     *
     * <p>返回一个校验后的对象而不是直接写库——调用方有两个，规则必须只有这一份；
     * 分开写两份的话，改动时漏一处就会出现「新增拦得住、编辑拦不住」。
     */
    private ValidatedPackage validate(AdminPackageRequest request) {
        String name = trim(request.getName());
        if (name.isEmpty()) {
            throw new BizException("请填写套餐名称");
        }
        if (request.getMonthlyFee() == null) {
            throw new BizException("请填写月费");
        }
        if (request.getMonthlyFee().signum() < 0) {
            throw new BizException("月费不能为负数");
        }
        if (request.getDataQuota() == null) {
            throw new BizException("请填写流量");
        }
        if (request.getDataQuota().signum() < 0) {
            throw new BizException("流量不能为负数");
        }
        if (request.getVoiceQuota() == null) {
            throw new BizException("请填写通话");
        }
        if (request.getVoiceQuota() < 0) {
            throw new BizException("通话不能为负数");
        }
        if (request.getSmsQuota() == null) {
            throw new BizException("请填写短信");
        }
        if (request.getSmsQuota() < 0) {
            throw new BizException("短信不能为负数");
        }

        // 与前端假数据阶段一致：只有明确的 0 才是停售，其余（含 null）按在售。
        int status = Integer.valueOf(0).equals(request.getStatus()) ? 0 : 1;
        return new ValidatedPackage(name, request.getMonthlyFee(), request.getDataQuota(),
                request.getVoiceQuota(), request.getSmsQuota(),
                trim(request.getDescription()), status);
    }

    /** 月费文案：39.00 → 39，避免下拉里出现「39.00 元」这种与假数据不一致的写法。 */
    private static String feeText(BigDecimal fee) {
        return fee == null ? "0" : fee.stripTrailingZeros().toPlainString();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private record ValidatedPackage(String name, BigDecimal monthlyFee, BigDecimal dataQuota,
                                    Integer voiceQuota, Integer smsQuota, String description,
                                    Integer status) {
    }
}
