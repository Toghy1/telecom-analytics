package com.demo01.business.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.demo01.business.auth.LoginContext;
import com.demo01.business.common.BizException;
import com.demo01.business.common.PageQuery;
import com.demo01.business.common.PageResult;
import com.demo01.business.common.PageSupport;
import com.demo01.business.dto.AdminAccountRequest;
import com.demo01.business.entity.Account;
import com.demo01.business.entity.Bill;
import com.demo01.business.entity.OrderRecord;
import com.demo01.business.entity.Package;
import com.demo01.business.entity.Ticket;
import com.demo01.business.mapper.AccountMapper;
import com.demo01.business.mapper.BillMapper;
import com.demo01.business.mapper.OrderRecordMapper;
import com.demo01.business.mapper.PackageMapper;
import com.demo01.business.mapper.TicketMapper;
import com.demo01.business.vo.AdminAccountVO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理后台 · 用户管理（《开发文档》§6.6）。
 *
 * <p>⚠️ 本类的查询口径与用户端**正好相反**：{@code AccountService.current()} 只看
 * 「我自己」，这里看**全部账号**，且不过滤 {@code status}——停用账号必须在
 * 列表里看得见，否则「启用」这个动作就无从谈起。
 *
 * <p>三条守卫与前端假数据阶段 {@code api/admin.js} 逐字一致：
 * <ol>
 *   <li>不能停用 / 删除**当前登录的账号**（自锁）；</li>
 *   <li>有账单 / 办理 / 工单数据的账号不能删除，改用「停用」；</li>
 *   <li>新增的电信用户必须是 11 位手机号（分析员 / 管理员用用户名）。</li>
 * </ol>
 * 前两条现在由**服务端**执行——假数据阶段它们是写在浏览器里的，绕过页面
 * 直接调接口就形同虚设。
 */
@Service
public class AdminAccountService {

    /** 与前端 {@code ROLE_LABEL} 的键一一对应。 */
    private static final Set<String> ROLES = Set.of("USER", "ANALYST", "ADMIN");

    private static final String ROLE_USER = "USER";

    /**
     * 新增账号的初始口令。演示系统统一用 {@code 123456}——与种子数据里
     * 500 个演示用户的口令一致，登录页的提示也只写这一句。
     *
     * <p>真实系统应由管理员在创建时设置或走短信激活，不该有「固定初始口令」；
     * 这里保留固定值是为了演示时「新建完就能用同一句提示登录」。
     */
    private static final String INITIAL_PASSWORD = "123456";

    private final AccountMapper accountMapper;
    private final PackageMapper packageMapper;
    private final BillMapper billMapper;
    private final OrderRecordMapper orderMapper;
    private final TicketMapper ticketMapper;
    private final PasswordEncoder passwordEncoder;

    public AdminAccountService(AccountMapper accountMapper, PackageMapper packageMapper,
                               BillMapper billMapper, OrderRecordMapper orderMapper,
                               TicketMapper ticketMapper, PasswordEncoder passwordEncoder) {
        this.accountMapper = accountMapper;
        this.packageMapper = packageMapper;
        this.billMapper = billMapper;
        this.orderMapper = orderMapper;
        this.ticketMapper = ticketMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 账号列表（分页 + 筛选）。
     *
     * <p>⚠️ 筛选在 SQL 里做，**不能**先查一页再在 Java 里过滤——那样
     * {@code total / pages} 算的还是过滤前的量，页面会出现「筛完只剩 3 条
     * 却显示共 502 条」。
     *
     * @param status 1 正常 / 0 停用；{@code null} = 全部。
     *               ⚠️ 不能把它折成 {@code if (status)} 这种真假判断：
     *               {@code 0} 是合法取值，那样写会把「停用」整档静默丢掉。
     * @param keyword 模糊匹配账号与归属地。匹配的是**库里的明文账号**：
     *                管理员输入手机号片段（如 {@code 1234}）能命中，
     *                输入页面上那串 {@code 138****1234} 则不会——星号只是展示态。
     */
    public PageResult<AdminAccountVO> list(int page, int size, String role,
                                           Integer status, String keyword) {
        String k = trim(keyword);
        LambdaQueryWrapper<Account> wrapper = Wrappers.<Account>lambdaQuery()
                .eq(!trim(role).isEmpty(), Account::getRole, trim(role))
                .eq(status != null, Account::getStatus, status)
                .and(!k.isEmpty(), q -> q.like(Account::getLoginName, k)
                        .or().like(Account::getRegion, k))
                // 管理端的账号列表读起来要像一本名册：按 id 升序，稳定可预期。
                .orderByAsc(Account::getId);

        Page<Account> first = accountMapper.selectPage(PageQuery.of(page, size), wrapper);
        PageResult<Account> result = PageSupport.of(first,
                n -> accountMapper.selectPage(PageQuery.of(n.intValue(), size), wrapper));

        Map<Long, String> names = packageNames(result.records());
        List<AdminAccountVO> rows = result.records().stream()
                // ⚠️ 先判 null 再查 Map：分析员 / 管理员没有 package_id，
                //    而 Map.of() 返回的不可变 Map 对 null 键是**直接抛 NPE** 的
                //    （不是返回 null）。整页列表会因此 500。
                .map(a -> AdminAccountVO.from(a,
                        a.getPackageId() == null ? null : names.get(a.getPackageId())))
                .toList();
        return PageResult.of(rows, result.total(), result.pages(), result.current());
    }

    /** 新增账号。明文手机号随请求体进来，入库前**不做任何脱敏**（脱敏只在出参）。 */
    public AdminAccountVO create(AdminAccountRequest request) {
        String role = trim(request.getRole());
        if (!ROLES.contains(role)) {
            throw new BizException("请选择账号角色");
        }

        String loginName = trim(request.getLoginName());
        if (loginName.isEmpty()) {
            throw new BizException("请填写账号");
        }
        // 电信用户以手机号入网（《开发文档》§5.2 ①：用户填手机号，管理员填用户名）。
        if (ROLE_USER.equals(role) && !loginName.matches("1\\d{10}")) {
            throw new BizException("电信用户请填写 11 位手机号");
        }
        if (accountMapper.selectCount(Wrappers.<Account>lambdaQuery()
                .eq(Account::getLoginName, loginName)) > 0) {
            throw new BizException("该账号已存在");
        }

        String idCard = trim(request.getIdCard());
        // 身份证可留空（真实入网也允许暂不补录），但填了就必须 18 位——
        // 否则它会被脱敏函数原样返回，明文落进列表。
        if (!idCard.isEmpty() && !idCard.matches("\\d{18}")) {
            throw new BizException("身份证号应为 18 位");
        }

        Long packageId = null;
        BigDecimal balance = null;
        if (ROLE_USER.equals(role)) {
            packageId = request.getPackageId();
            if (packageId != null && packageMapper.selectById(packageId) == null) {
                throw new BizException("套餐不存在");
            }
            balance = request.getBalance() == null ? BigDecimal.ZERO : request.getBalance();
            if (balance.signum() < 0) {
                throw new BizException("余额不能为负数");
            }
        }

        Account account = new Account();
        account.setLoginName(loginName);
        account.setPassword(passwordEncoder.encode(INITIAL_PASSWORD));
        account.setRole(role);
        account.setRegion(emptyToNull(request.getRegion()));
        // 分析员 / 管理员没有身份证、套餐、余额这三个字段（「最小必要」），一律置空。
        account.setIdCard(ROLE_USER.equals(role) ? emptyToNull(idCard) : null);
        account.setPackageId(packageId);
        account.setBalance(balance);
        account.setStatus(1); // 新建账号一律正常，要停用走「停用」按钮
        account.setCreateTime(LocalDateTime.now());
        accountMapper.insert(account);

        String packageName = packageId == null ? null : packageName(packageId);
        return AdminAccountVO.from(account, packageName);
    }

    /**
     * 启用 / 停用账号。
     *
     * <p>⚠️ 拒绝改动**当前登录的管理员自己**。这不是怕把系统锁死（还能换一个
     * 管理员账号登进来），而是与「删除账号」共用同一条业务纪律：
     * **不能把当前操作者自己搞掉**，两处措辞一致，演示时讲一次就够。
     */
    public AdminAccountVO updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException("状态取值非法");
        }
        Account account = require(id);
        if (Objects.equals(id, LoginContext.userId())) {
            throw new BizException("不能停用当前登录的账号");
        }

        // 只写 status 一列：整行 updateById 会把刚才读到的其余字段原样写回，
        // 两个管理员同时操作时会把对方的改动盖掉。
        Account patch = new Account();
        patch.setId(account.getId());
        patch.setStatus(status);
        accountMapper.updateById(patch);
        account.setStatus(status);
        return AdminAccountVO.from(account, packageName(account.getPackageId()));
    }

    /** 重置为初始口令。出参只有「成功」，**不回传新口令**。 */
    public boolean resetPassword(Long id) {
        Account account = require(id);
        Account patch = new Account();
        patch.setId(account.getId());
        patch.setPassword(passwordEncoder.encode(INITIAL_PASSWORD));
        accountMapper.updateById(patch);
        return true;
    }

    /**
     * 删除账号。
     *
     * <p>⚠️ 没有外键就去掉守卫的话，删除一个办过业务的账号会让账单 / 办理 /
     * 工单出现指向不存在用户的「孤儿行」——页面不报错，只是「提交人」显示成
     * 未知。所以这里照假数据阶段的两道守卫办事：自锁、有业务数据就拒绝。
     *
     * <p>种子账号 1001 / 1002 必然被第二道拒绝，这是**常态**。演示「删除成功」
     * 的正向路径是：新建一个账号，再删掉它。
     */
    public boolean delete(Long id) {
        require(id);
        if (Objects.equals(id, LoginContext.userId())) {
            throw new BizException("不能删除当前登录的账号");
        }

        boolean used = billMapper.selectCount(Wrappers.<Bill>lambdaQuery()
                        .eq(Bill::getUserId, id)) > 0
                || orderMapper.selectCount(Wrappers.<OrderRecord>lambdaQuery()
                        .eq(OrderRecord::getUserId, id)) > 0
                || ticketMapper.selectCount(Wrappers.<Ticket>lambdaQuery()
                        .eq(Ticket::getUserId, id)) > 0;
        if (used) {
            throw new BizException("该账号存在业务数据（账单 / 办理 / 工单），无法删除，请改用「停用」");
        }

        accountMapper.deleteById(id);
        return true;
    }

    /* ------------------------------------------------------------------ */

    private Account require(Long id) {
        Account account = id == null ? null : accountMapper.selectById(id);
        if (account == null) {
            throw new BizException("账号不存在");
        }
        return account;
    }

    /**
     * 批量解析本页涉及的套餐名。
     *
     * <p>⚠️ 用 {@code selectBatchIds} 而**不是**「在售套餐列表」：已停售的套餐
     * （种子里的「学生特惠 29 元」）仍被存量账号持有，按 {@code status = 1}
     * 过滤会让那一行的「当前套餐」变成空白——不报错，只是少显示一个名字。
     */
    private Map<Long, String> packageNames(List<Account> accounts) {
        List<Long> ids = accounts.stream()
                .map(Account::getPackageId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return packageMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Package::getId, Package::getName));
    }

    private String packageName(Long packageId) {
        if (packageId == null) {
            return null;
        }
        Package pkg = packageMapper.selectById(packageId);
        return pkg == null ? null : pkg.getName();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String emptyToNull(String value) {
        String s = trim(value);
        return s.isEmpty() ? null : s;
    }
}
