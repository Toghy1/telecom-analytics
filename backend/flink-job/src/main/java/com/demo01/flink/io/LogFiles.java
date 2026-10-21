package com.demo01.flink.io;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;

/**
 * 日文件的小工具：从文件名取北京日期、取最大日期作为统计日。
 *
 * <p>{@code stat_date} 用的是**文件日期的最大值**，与 {@code etl.py}
 * 的 {@code max(per_day)} 同口径；不取明细表最大值，因为实时链路也在往
 * {@code dwd_behavior_log} 写当天数据，会把锚点带偏。
 */
public final class LogFiles {

    private static final String PREFIX = "behavior_";
    private static final String SUFFIX = ".jsonl";

    public static LocalDate maxDay(String inputDir) throws Exception {
        LocalDate max = null;
        int count = 0;
        DirectoryStream<Path> stream =
                Files.newDirectoryStream(Paths.get(inputDir), PREFIX + "*" + SUFFIX);
        try {
            for (Path p : stream) {
                count++;
                LocalDate d = dayOf(p);
                if (max == null || d.isAfter(max)) {
                    max = d;
                }
            }
        } finally {
            stream.close();
        }
        if (max == null) {
            throw new IllegalStateException("目录下没有 " + PREFIX + "*" + SUFFIX + "：" + inputDir);
        }
        if (count == 0) {
            throw new IllegalStateException("没有可读的日文件：" + inputDir);
        }
        return max;
    }

    public static LocalDate dayOf(Path file) {
        String name = file.getFileName().toString();
        return LocalDate.parse(name.substring(PREFIX.length(), name.length() - SUFFIX.length()));
    }

    private LogFiles() {
    }
}
