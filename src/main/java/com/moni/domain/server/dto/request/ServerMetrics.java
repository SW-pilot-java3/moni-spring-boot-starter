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

        // HTTP (WAS) — 엔드포인트(uri·method·status) 단위로 나눠서 담는다
        List<HttpEndpointMetrics> httpEndpoints,

        // HikariCP — 풀(DataSource) 단위로 나눠서 담는다
        List<HikariPoolMetrics> hikaricpPools,

        // Executor — 스레드풀 이름 단위로 나눠서 담는다
        List<ExecutorMetrics> executors) {
}