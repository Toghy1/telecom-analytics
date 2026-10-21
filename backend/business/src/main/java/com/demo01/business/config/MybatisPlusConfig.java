package com.demo01.business.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置。
 *
 * <p>⚠️ **分页插件必须显式注册**。不注册时 {@code selectPage()} 不会报错，
 * 它只是**静默返回全量数据**、`total` 恒为 0——列表页看起来「有数据」，
 * 翻页却不生效，且任何一处都没有异常信息（选型②「三处默认行为与既有表
 * 设计方向相反」之三）。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
