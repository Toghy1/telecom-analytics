package com.demo01.business.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 全局时间序列化格式：{@code YYYY-MM-DD HH:mm:ss}。
 *
 * <p>⚠️ 这一项不是「好看」，是**全站时间口径的一致性**（见前端
 * {@code utils/datetime.js} 顶部说明）：页面把 {@code create_time}、
 * {@code order_time} 这类字段当**定宽字符串**处理（`localeCompare` 排序、
 * `<=` 比较档期），格式一旦是 Jackson 默认的 ISO-8601（`2026-10-21T10:48:32`），
 * 页面上会多出一个 `T`，且与库里存的、与假数据时期的写法分成两种。
 *
 * <p>⚠️ {@code spring.jackson.date-format} **管不到** {@code LocalDateTime}
 * （那一项只作用于 {@code java.util.Date}），必须像这样显式注册序列化器。
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer localDateTimeCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule();
            module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(FORMATTER));
            module.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(FORMATTER));
            builder.modules(module);
        };
    }
}
