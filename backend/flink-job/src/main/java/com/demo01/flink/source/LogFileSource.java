package com.demo01.flink.source;

import com.demo01.flink.model.RawLine;
import org.apache.flink.streaming.api.functions.source.RichSourceFunction;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 有界日志源：按文件名升序读取目录下全部 {@code behavior_*.jsonl}。
 *
 * <p>顺序不是可选项：规则⑥的「第一条正常入库」依赖它。文件名是
 * {@code behavior_2026-10-06.jsonl} 这种零填充日期，字典序即时间序；
 * 文件内按行顺序读。整个 source 单并行度，读完后发一个结束标记。
 *
 * <p>当前阶段直接读本地目录（对应文档里「HDFS 可裁剪、Spark/Flink 直接读
 * 本地文件」的降级口径）。上 VM 后若保留 HDFS，只需把这里换成 Flink 的
 * FileSource / HDFS 路径，后续算子不用动。
 */
public class LogFileSource extends RichSourceFunction<RawLine> {

    private static final long serialVersionUID = 1L;

    private static final String PREFIX = "behavior_";
    private static final String SUFFIX = ".jsonl";

    private final String inputDir;
    private volatile boolean running = true;

    public LogFileSource(String inputDir) {
        this.inputDir = inputDir;
    }

    @Override
    public void run(SourceContext<RawLine> ctx) throws Exception {
        List<Path> files = listFiles();
        if (files.isEmpty()) {
            throw new IllegalStateException("目录下没有 " + PREFIX + "*" + SUFFIX + "：" + inputDir);
        }

        for (Path file : files) {
            if (!running) {
                return;
            }
            LocalDate day = dayOf(file);
            BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8);
            try {
                String line;
                while (running && (line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) {
                        continue;
                    }
                    ctx.collect(new RawLine(day, line));
                }
            } finally {
                reader.close();
            }
        }

        if (running) {
            ctx.collect(RawLine.end());
        }
    }

    @Override
    public void cancel() {
        running = false;
    }

    private List<Path> listFiles() throws Exception {
        List<Path> files = new ArrayList<Path>();
        DirectoryStream<Path> stream =
                Files.newDirectoryStream(Paths.get(inputDir), PREFIX + "*" + SUFFIX);
        try {
            for (Path p : stream) {
                files.add(p);
            }
        } finally {
            stream.close();
        }
        Collections.sort(files);
        return files;
    }

    private static LocalDate dayOf(Path file) {
        String name = file.getFileName().toString();
        return LocalDate.parse(name.substring(PREFIX.length(), name.length() - SUFFIX.length()));
    }
}
