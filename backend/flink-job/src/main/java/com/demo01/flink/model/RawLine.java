package com.demo01.flink.model;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 一行原始日志 + 它来自哪个日文件。
 *
 * <p>文件日期不是装饰：死信表的 {@code fail_time} 按「日文件 + 5 分钟」生成，
 * 与 {@code etl/etl.py} 的批次时间口径一致；同时用于校验 event_time 换算出的
 * 北京日期是否与文件名对得上。
 *
 * <p>{@link #end()} 是**有界流的结束标记**。Flink 的 source 读完文件后不会
 * 自动告诉下游「没有更多数据了」，而漏斗 / 留存这类指标必须等全量读完才能
 * 出结果——所以显式发一个标记，由指标聚合算子收到后一次性吐出所有行。
 */
public class RawLine implements Serializable {

    private static final long serialVersionUID = 1L;

    private LocalDate fileDay;
    private String line;
    private boolean end;

    public RawLine() {
    }

    public RawLine(LocalDate fileDay, String line) {
        this.fileDay = fileDay;
        this.line = line;
    }

    private RawLine(boolean end) {
        this.end = end;
    }

    public static RawLine end() {
        return new RawLine(true);
    }

    public LocalDate getFileDay() {
        return fileDay;
    }

    public void setFileDay(LocalDate fileDay) {
        this.fileDay = fileDay;
    }

    public String getLine() {
        return line;
    }

    public void setLine(String line) {
        this.line = line;
    }

    public boolean isEnd() {
        return end;
    }

    public void setEnd(boolean end) {
        this.end = end;
    }
}
