package com.moni.domain.server.dto;

import lombok.Builder;

@Builder
public record ServerMetrics(
        // JVM
        Long jvmHeapUsedBytes,
        Long jvmHeapMaxBytes,
        Long jvmOldGenUsedBytes,
        Long gcPauseSecondsCount,
        Double gcPauseSecondsSum,
        Double processUptimeSeconds,
        Integer jvmThreadsLive,
        Integer jvmThreadsBlocked,

        // HTTP (WAS)
        Long httpRequestsCount,
        Double httpRequestsSum,
        Double httpRequestsMax,
        Long httpErrorsCount,
        Integer tomcatThreadsBusy,
        Integer tomcatThreadsConfigMax,

        // HikariCP
        Integer hikaricpActive,
        Integer hikaricpIdle,
        Integer hikaricpPending,
        Integer hikaricpMax,
        Long hikaricpTimeoutsTotal,

        // Executor
        Integer executorActive,
        Integer executorMax,
        Integer executorQueuedTasks,
        Integer executorQueueRemaining) {
}
