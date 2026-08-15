package com.moni.domain.server.dto.request;

import java.util.List;
import lombok.Builder;

@Builder
public record ServerMetrics(
        Long jvmHeapUsedBytes,
        Long jvmHeapMaxBytes,
        Long jvmOldGenUsedBytes,
        Long gcPauseSecondsCount,
        Double gcPauseSecondsSum,
        Double processUptimeSeconds,
        Integer jvmThreadsLive,
        Integer jvmThreadsBlocked,
        List<HttpEndpointMetrics> httpEndpoints,
        List<HikariPoolMetrics> hikaricpPools,
        List<ExecutorMetrics> executors) {
}