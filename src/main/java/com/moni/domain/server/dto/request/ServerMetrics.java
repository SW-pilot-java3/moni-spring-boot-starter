package com.moni.domain.server.dto.request;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
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

    public static ServerMetrics from(MeterRegistry registry) {
        Collection<Timer> httpTimers = registry.find("http.server.requests").timers();

        return ServerMetrics.builder()
                .jvmHeapUsedBytes(sumGauges(registry))
                .jvmHeapMaxBytes(sumPositiveGauges(registry))
                .jvmOldGenUsedBytes(oldGenUsedBytes(registry))
                .gcPauseSecondsCount(gcPauseSecondsCount(registry))
                .gcPauseSecondsSum(gcPauseSecondsSum(registry))
                .processUptimeSeconds(sumGaugeValue(registry, "process.uptime"))
                .jvmThreadsLive(sumIntGaugeValue(registry, "jvm.threads.live"))
                .jvmThreadsBlocked(sumIntGaugeValue(registry, "jvm.threads.states", "state", "blocked"))
                .httpRequestsCount(httpRequestsCount(httpTimers))
                .httpRequestsSum(httpRequestsSum(httpTimers))
                .httpRequestsMax(httpRequestsMax(httpTimers))
                .httpErrorsCount(httpErrorsCount(httpTimers))
                .tomcatThreadsBusy(sumIntGaugeValue(registry, "tomcat.threads.busy"))
                .tomcatThreadsConfigMax(sumIntGaugeValue(registry, "tomcat.threads.config.max"))
                .hikaricpPools(hikariPoolMetrics(registry))
                .executorActive(sumIntGaugeValue(registry, "executor.active"))
                .executorMax(sumIntGaugeValue(registry, "executor.pool.max"))
                .executorQueuedTasks(sumIntGaugeValue(registry, "executor.queued"))
                .executorQueueRemaining(sumIntGaugeValue(registry, "executor.queue.remaining"))
                .build();
    }

    private static List<HikariPoolMetrics> hikariPoolMetrics(MeterRegistry registry) {
        Set<String> poolNames = registry.find("hikaricp.connections.active").gauges().stream()
                .map(gauge -> gauge.getId().getTag("pool"))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return poolNames.stream()
                .map(poolName -> HikariPoolMetrics.builder()
                        .poolName(poolName)
                        .active(intGaugeValueForPool(registry, "hikaricp.connections.active", poolName))
                        .idle(intGaugeValueForPool(registry, "hikaricp.connections.idle", poolName))
                        .pending(intGaugeValueForPool(registry, "hikaricp.connections.pending", poolName))
                        .max(intGaugeValueForPool(registry, "hikaricp.connections.max", poolName))
                        .timeoutsTotal(counterValueForPool(registry, "hikaricp.connections.timeout", poolName))
                        .build())
                .toList();
    }

    private static Integer intGaugeValueForPool(MeterRegistry registry, String name, String poolName) {
        Gauge gauge = registry.find(name).tag("pool", poolName).gauge();
        if (gauge == null) {
            return null;
        }
        double value = gauge.value();
        return Double.isNaN(value) ? null : (int) value;
    }

    private static Long counterValueForPool(MeterRegistry registry, String name, String poolName) {
        Counter counter = registry.find(name).tag("pool", poolName).counter();
        return counter != null ? (long) counter.count() : null;
    }

    private static Long httpRequestsCount(Collection<Timer> httpTimers) {
        return httpTimers.isEmpty() ? null : httpTimers.stream().mapToLong(Timer::count).sum();
    }

    private static Double httpRequestsSum(Collection<Timer> httpTimers) {
        return httpTimers.isEmpty() ? null
                : httpTimers.stream().mapToDouble(timer -> timer.totalTime(TimeUnit.SECONDS)).sum();
    }

    private static Double httpRequestsMax(Collection<Timer> httpTimers) {
        return httpTimers.isEmpty() ? null
                : httpTimers.stream().mapToDouble(timer -> timer.max(TimeUnit.SECONDS)).max().getAsDouble();
    }

    private static Long httpErrorsCount(Collection<Timer> httpTimers) {
        if (httpTimers.isEmpty()) {
            return null;
        }
        return httpTimers.stream()
                .filter(ServerMetrics::isErrorStatus)
                .mapToLong(Timer::count)
                .sum();
    }

    private static boolean isErrorStatus(Timer timer) {
        String status = timer.getId().getTag("status");
        return status != null && (status.startsWith("4") || status.startsWith("5"));
    }

    private static Long oldGenUsedBytes(MeterRegistry registry) {
        // GC 종류별로 old gen pool의 id 태그명이 다름 (G1: "G1 Old Gen", Parallel: "PS Old Gen", Serial: "Tenured Gen")
        return registry.find("jvm.memory.used").tag("area", "heap").gauges().stream()
                .filter(gauge -> isOldGenPool(gauge.getId().getTag("id")))
                .findFirst()
                .map(Gauge::value)
                .filter(value -> !Double.isNaN(value))
                .map(value -> (long) (double) value)
                .orElse(null);
    }

    private static boolean isOldGenPool(String poolId) {
        return poolId != null && (poolId.contains("Old") || poolId.contains("Tenured"));
    }

    private static Long gcPauseSecondsCount(MeterRegistry registry) {
        Collection<Timer> timers = registry.find("jvm.gc.pause").timers();
        return timers.isEmpty() ? null : timers.stream().mapToLong(Timer::count).sum();
    }

    private static Double gcPauseSecondsSum(MeterRegistry registry) {
        Collection<Timer> timers = registry.find("jvm.gc.pause").timers();
        return timers.isEmpty() ? null
                : timers.stream().mapToDouble(timer -> timer.totalTime(TimeUnit.SECONDS)).sum();
    }

    private static Long sumGauges(MeterRegistry registry) {
        Collection<Gauge> gauges = registry.find("jvm.memory.used").tag("area", "heap").gauges();
        if (gauges.isEmpty()) {
            return null;
        }
        return (long) gauges.stream().mapToDouble(Gauge::value).sum();
    }

    private static Long sumPositiveGauges(MeterRegistry registry) {
        // 일부 메모리 풀은 max가 정의되지 않으면 -1을 반환하므로 합산에서 제외한다
        Collection<Gauge> gauges = registry.find("jvm.memory.max").tag("area", "heap").gauges();
        if (gauges.isEmpty()) {
            return null;
        }
        double sum = gauges.stream().mapToDouble(Gauge::value).filter(value -> value >= 0).sum();
        return (long) sum;
    }

    private static Double sumGaugeValue(MeterRegistry registry, String name, String... tags) {
        List<Double> values = registry.find(name).tags(tags).gauges().stream()
                .map(Gauge::value)
                .filter(value -> !Double.isNaN(value))
                .toList();
        return values.isEmpty() ? null : values.stream().mapToDouble(Double::doubleValue).sum();
    }

    private static Integer sumIntGaugeValue(MeterRegistry registry, String name, String... tags) {
        Double value = sumGaugeValue(registry, name, tags);
        return value != null ? (int) (double) value : null;
    }
}