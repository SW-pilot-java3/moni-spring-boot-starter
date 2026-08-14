package com.moni.domain.server.dto.request;

import java.util.List;
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

        // HikariCP — 풀(DataSource)이 여러 개일 수 있어 풀 단위로 나눠서 담는다
        List<HikariPoolMetrics> hikaricpPools,

        // Executor
        Integer executorActive,
        Integer executorMax,
        Integer executorQueuedTasks,
        Integer executorQueueRemaining) {
}
