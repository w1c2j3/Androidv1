package com.shiliuai.util;

import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public final class Ids {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd")
            .withZone(ZoneId.of("Asia/Shanghai"));

    private Ids() {
    }

    public static String botId(Clock clock) {
        return "bot_" + DAY.format(clock.instant()) + "_" + shortUuid();
    }

    public static String traceId(Clock clock, String source) {
        String safeSource = source == null || source.isBlank() ? "unknown" : source.replaceAll("[^a-zA-Z0-9]", "_");
        return "trace_" + DAY.format(clock.instant()) + "_" + safeSource + "_" + shortUuid();
    }

    public static String fileId(Clock clock) {
        return "file_" + DAY.format(clock.instant()) + "_" + shortUuid();
    }

    public static String taskId(Clock clock) {
        return "task_" + DAY.format(clock.instant()) + "_" + shortUuid();
    }

    public static String runId(Clock clock) {
        return "run_" + DAY.format(clock.instant()) + "_" + shortUuid();
    }

    public static String memoryId(Clock clock) {
        return "mem_" + DAY.format(clock.instant()) + "_" + shortUuid();
    }

    public static String cardActionLogId(Clock clock) {
        return "card_action_" + DAY.format(clock.instant()) + "_" + shortUuid();
    }

    public static String reportMaterialId(Clock clock) {
        return "report_" + DAY.format(clock.instant()) + "_" + shortUuid();
    }

    private static String shortUuid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
