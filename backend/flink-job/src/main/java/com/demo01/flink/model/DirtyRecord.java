package com.demo01.flink.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 一条脏数据，原样保留在 {@code dwd_behavior_dirty}，不静默丢弃。
 *
 * <p>{@code failReason} 的取值是前端 {@code DIRTY_RULE} 的七个枚举之一：
 * MISSING_REQUIRED / ENUM_OUT_OF_RANGE / TYPE_ERROR / TIME_ILLEGAL /
 * ID_FORMAT_ERROR / DUPLICATE / OVERLONG_GARBLED。
 */
public class DirtyRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private String failReason;
    private String rawContent;
    private LocalDateTime failTime;

    public DirtyRecord() {
    }

    public DirtyRecord(String failReason, String rawContent, LocalDateTime failTime) {
        this.failReason = failReason;
        this.rawContent = rawContent;
        this.failTime = failTime;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }

    public String getRawContent() {
        return rawContent;
    }

    public void setRawContent(String rawContent) {
        this.rawContent = rawContent;
    }

    public LocalDateTime getFailTime() {
        return failTime;
    }

    public void setFailTime(LocalDateTime failTime) {
        this.failTime = failTime;
    }
}
