package com.demo01.flink.model;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 一条通过七条判脏规则的干净事件，字段与 {@code dwd_behavior_log} 一一对应。
 *
 * <p>{@code eventDate} 是**北京日期**（分区列），{@code eventTime} 是 **UTC**。
 * 两者不能互相推导——凌晨 0—8 点的 UTC 时间换算到北京会落到第二天。
 */
public class Event implements Serializable {

    private static final long serialVersionUID = 1L;

    private String eventId;
    private LocalDate eventDate;
    private String eventCode;
    private LocalDateTime eventTime;
    private Long userId;
    private String sessionId;
    private String page;
    private String region;
    private String device;
    private String ip;
    private String ext;
    private boolean end;

    public Event() {
    }

    private Event(boolean end) {
        this.end = end;
    }

    public static Event end() {
        return new Event(true);
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public LocalDate getEventDate() {
        return eventDate;
    }

    public void setEventDate(LocalDate eventDate) {
        this.eventDate = eventDate;
    }

    public String getEventCode() {
        return eventCode;
    }

    public void setEventCode(String eventCode) {
        this.eventCode = eventCode;
    }

    public LocalDateTime getEventTime() {
        return eventTime;
    }

    public void setEventTime(LocalDateTime eventTime) {
        this.eventTime = eventTime;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getPage() {
        return page;
    }

    public void setPage(String page) {
        this.page = page;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getDevice() {
        return device;
    }

    public void setDevice(String device) {
        this.device = device;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getExt() {
        return ext;
    }

    public void setExt(String ext) {
        this.ext = ext;
    }

    public boolean isEnd() {
        return end;
    }

    public void setEnd(boolean end) {
        this.end = end;
    }
}
