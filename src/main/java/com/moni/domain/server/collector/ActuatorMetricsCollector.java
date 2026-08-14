package com.moni.domain.server.collector;

import com.moni.domain.server.dto.request.HikariPoolMetrics;
import com.moni.domain.server.dto.request.ServerMetrics;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class ActuatorMetricsCollector implements ServerMetricsCollector {

    private final MeterRegistry registry;

    @Override
    public ServerMetrics collect() {
        try {
            return extract();
        } catch (Exception e) {
            log.warn("Failed to collect server metrics from MeterRegistry", e);
            return null;
        }
    }

    private ServerMetrics extract() {
        Collection<Timer> httpTimers = registry.find("http.server.requests").timers();

        return ServerMetrics.builder()
                .jvmHeapUsedBytes(sumGauges())
                .jvmHeapMaxBytes(sumPositiveGauges())
                .jvmOldGenUsedBytes(oldGenUsedBytes())
                .gcPauseSecondsCount(gcPauseSecondsCount())
                .gcPauseSecondsSum(gcPauseSecondsSum())
                .processUptimeSeconds(sumGaugeValue("process.uptime"))
                .jvmThreadsLive(sumIntGaugeValue("jvm.threads.live"))
                .jvmThreadsBlocked(sumIntGaugeValue("jvm.threads.states", "state", "blocked"))
                .httpRequestsCount(httpRequestsCount(httpTimers))
                .httpRequestsSum(httpRequestsSum(httpTimers))
                .httpRequestsMax(httpRequestsMax(httpTimers))
                .httpErrorsCount(httpErrorsCount(httpTimers))
                .tomcatThreadsBusy(sumIntGaugeValue("tomcat.threads.busy"))
                .tomcatThreadsConfigMax(sumIntGaugeValue("tomcat.threads.config.max"))
                .hikaricpPools(hikariPoolMetrics())
                .executorActive(sumIntGaugeValue("executor.active"))
                .executorMax(sumIntGaugeValue("executor.pool.max"))
                .executorQueuedTasks(sumIntGaugeValue("executor.queued"))
                .executorQueueRemaining(sumIntGaugeValue("executor.queue.remaining"))
                .build();
    }

    private List<HikariPoolMetrics> hikariPoolMetrics() {
        Set<String> poolNames = registry.find("hikaricp.connections.active").gauges().stream()
                .map(gauge -> gauge.getId().getTag("pool"))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return poolNames.stream()
                .map(poolName -> HikariPoolMetrics.builder()
                        .poolName(poolName)
                        .active(intGaugeValueForPool("hikaricp.connections.active", poolName))
                        .idle(intGaugeValueForPool("hikaricp.connections.idle", poolName))
                        .pending(intGaugeValueForPool("hikaricp.connections.pending", poolName))
                        .max(intGaugeValueForPool("hikaricp.connections.max", poolName))
                        .timeoutsTotal(counterValueForPool("hikaricp.connections.timeout", poolName))
                        .build())
                .toList();
    }

    private Integer intGaugeValueForPool(String name, String poolName) {
        Gauge gauge = registry.find(name).tag("pool", poolName).gauge();
        if (gauge == null) {
            return null;
        }
        double value = gauge.value();
        return Double.isNaN(value) ? null : (int) value;
    }

    private Long counterValueForPool(String name, String poolName) {
        Counter counter = registry.find(name).tag("pool", poolName).counter();
        return counter != null ? (long) counter.count() : null;
    }

    private Long httpRequestsCount(Collection<Timer> httpTimers) {
        return httpTimers.isEmpty() ? null : httpTimers.stream().mapToLong(Timer::count).sum();
    }

    private Double httpRequestsSum(Collection<Timer> httpTimers) {
        return httpTimers.isEmpty() ? null
                : httpTimers.stream().mapToDouble(timer -> timer.totalTime(TimeUnit.SECONDS)).sum();
    }

    private Double httpRequestsMax(Collection<Timer> httpTimers) {
        return httpTimers.isEmpty() ? null
                : httpTimers.stream().mapToDouble(timer -> timer.max(TimeUnit.SECONDS)).max().getAsDouble();
    }

    private Long httpErrorsCount(Collection<Timer> httpTimers) {
        if (httpTimers.isEmpty()) {
            return null;
        }
        return httpTimers.stream()
                .filter(ActuatorMetricsCollector::isErrorStatus)
                .mapToLong(Timer::count)
                .sum();
    }

    private static boolean isErrorStatus(Timer timer) {
        String status = timer.getId().getTag("status");
        return status != null && (status.startsWith("4") || status.startsWith("5"));
    }

    private Long oldGenUsedBytes() {
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

    private Long gcPauseSecondsCount() {
        Collection<Timer> timers = registry.find("jvm.gc.pause").timers();
        return timers.isEmpty() ? null : timers.stream().mapToLong(Timer::count).sum();
    }

    private Double gcPauseSecondsSum() {
        Collection<Timer> timers = registry.find("jvm.gc.pause").timers();
        return timers.isEmpty() ? null
                : timers.stream().mapToDouble(timer -> timer.totalTime(TimeUnit.SECONDS)).sum();
    }

    private Long sumGauges() {
        Collection<Gauge> gauges = registry.find("jvm.memory.used").tag("area", "heap").gauges();
        if (gauges.isEmpty()) {
            return null;
        }
        return (long) gauges.stream().mapToDouble(Gauge::value).sum();
    }

    private Long sumPositiveGauges() {
        // 일부 메모리 풀은 max가 정의되지 않으면 -1을 반환하므로 합산에서 제외한다
        Collection<Gauge> gauges = registry.find("jvm.memory.max").tag("area", "heap").gauges();
        if (gauges.isEmpty()) {
            return null;
        }
        double sum = gauges.stream().mapToDouble(Gauge::value).filter(value -> value >= 0).sum();
        return (long) sum;
    }

    private Double sumGaugeValue(String name, String... tags) {
        List<Double> values = registry.find(name).tags(tags).gauges().stream()
                .map(Gauge::value)
                .filter(value -> !Double.isNaN(value))
                .toList();
        return values.isEmpty() ? null : values.stream().mapToDouble(Double::doubleValue).sum();
    }

    private Integer sumIntGaugeValue(String name, String... tags) {
        Double value = sumGaugeValue(name, tags);
        return value != null ? (int) (double) value : null;
    }
}
