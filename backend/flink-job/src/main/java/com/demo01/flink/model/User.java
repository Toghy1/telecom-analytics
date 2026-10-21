package com.demo01.flink.model;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * {@code users.json} 里的一条用户记录，只保留离线指标用得上的字段。
 *
 * <p>三个用户维度分布（地域 / 套餐 / 活跃度）的分母都是这份全量名册，
 * 三者合计必须相等；留存也以 {@code registerDate} 作为 cohort 锚点。
 */
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private String loginName;
    private String region;
    private Long packageId;
    private LocalDate registerDate;

    public User() {
    }

    public User(Long userId, String loginName, String region, Long packageId, LocalDate registerDate) {
        this.userId = userId;
        this.loginName = loginName;
        this.region = region;
        this.packageId = packageId;
        this.registerDate = registerDate;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getLoginName() {
        return loginName;
    }

    public void setLoginName(String loginName) {
        this.loginName = loginName;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public Long getPackageId() {
        return packageId;
    }

    public void setPackageId(Long packageId) {
        this.packageId = packageId;
    }

    public LocalDate getRegisterDate() {
        return registerDate;
    }

    public void setRegisterDate(LocalDate registerDate) {
        this.registerDate = registerDate;
    }
}
