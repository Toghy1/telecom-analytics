package com.demo01.flink.config;

import java.io.Serializable;

/**
 * 只描述「MySQL 连到哪」的最小接口。
 *
 * <p>离线作业与实时作业各有一套自己的参数类（{@link JobConfig} /
 * {@link RealtimeConfig}），但 sink 和重复校验只需要这三个取值。
 * 抽成接口后，sink 不必知道自己是离线还是实时，也不用为了复用去继承
 * 一堆用不到的字段。
 */
public interface JdbcConfig extends Serializable {

    String jdbcUrl();

    String getUser();

    String getPassword();
}
