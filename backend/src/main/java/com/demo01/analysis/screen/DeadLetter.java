package com.demo01.analysis.screen;

import java.time.LocalDateTime;

/** `dwd_behavior_dirty` 的一行，形状与前端死信表格一致。 */
public record DeadLetter(
        long id,
        String source,
        String raw_content,
        String fail_reason,
        LocalDateTime fail_time) {
}
