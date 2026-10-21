package com.demo01.business.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.demo01.business.auth.JwtUtil;
import com.demo01.business.auth.TokenStore;
import com.demo01.business.common.BizException;
import com.demo01.business.dto.LoginRequest;
import com.demo01.business.entity.Account;
import com.demo01.business.mapper.AccountMapper;
import com.demo01.business.vo.AccountVO;
import com.demo01.business.vo.LoginVO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 登录 / 登出（选型③）。
 *
 * <p>⚠️ 登录标识**按角色不同**：电信用户填手机号，分析员 / 管理员填用户名。
 * 库里 {@code t_account.login_name} 一列同时存这两种，SQL 上不分叉——
 * 分叉的只是「用户该输什么」这层界面约定（登录页已按角色切换输入框文案）。
 */
@Service
public class AuthService {

    private final AccountMapper accountMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final TokenStore tokenStore;

    public AuthService(AccountMapper accountMapper, PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil, TokenStore tokenStore) {
        this.accountMapper = accountMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.tokenStore = tokenStore;
    }

    public LoginVO login(LoginRequest request) {
        Account account = accountMapper.selectOne(
                Wrappers.<Account>lambdaQuery().eq(Account::getLoginName, request.getLoginName().trim()));

        // ⚠️ 「账号不存在」与「密码不对」必须报**同一句话**：分开报等于
        //    提供一个「拿手机号试出哪些是真实用户」的接口。
        if (account == null || !passwordEncoder.matches(request.getPassword(), account.getPassword())) {
            throw new BizException("账号或密码不正确");
        }

        // status 1=正常 0=停用。停用账号能登进去看自己的历史，只是不能办业务——
        // 那是另一层判断；这里直接拦住，避免一个停用账号还能拿到 token。
        if (account.getStatus() == null || account.getStatus() != 1) {
            throw new BizException("该账号已停用，请前往营业厅办理");
        }

        String token = jwtUtil.create(account.getId(), account.getRole());
        // 白名单是「这个 token 还有效」的唯一来源：退出登录后验签照样能过，
        // 只有把它从白名单删掉才是真的失效。
        tokenStore.put(token, jwtUtil.getTtlMillis());

        return new LoginVO(token, AccountVO.from(account));
    }

    /** 退出登录。token 已在拦截器里验过，这里只需从白名单摘掉。 */
    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            tokenStore.remove(token);
        }
    }
}
