package com.moni.domain.server.collector;

import com.moni.domain.server.dto.request.ExecutorMetrics;
import com.moni.domain.server.dto.request.HikariPoolMetrics;
import com.moni.domain.server.dto.request.HttpEndpointMetrics;
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

    private static final String INTERNAL_MONITORING_URI_PREFIX = "/actuator";

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
        return ServerMetrics.builder()
                .jvmHeapUsedBytes(sumGauges())
                .jvmHeapMaxBytes(sumPositiveGauges())
                .jvmOldGenUsedBytes(oldGenUsedBytes())
                .gcPauseSecondsCount(gcPauseSecondsCount())
                .gcPauseSecondsSum(gcPauseSecondsSum())
                .processUptimeSeconds(sumGaugeValue("process.uptime"))
                .jvmThreadsLive(sumIntGaugeValue("jvm.threads.live"))
                .jvmThreadsBlocked(sumIntGaugeValue("jvm.threads.states", "state", "blocked"))
                .httpEndpoints(httpEndpointMetrics())
                .hikaricpPools(hikariPoolMetrics())
                .executors(executorMetrics())
                .build();
    }

    private List<HttpEndpointMetrics> httpEndpointMetrics() {
        return registry.find("http.server.requests").timers().stream()
                .filter(timer -> !isInternalMonitoringUri(timer.getId().getTag("uri")))
                .map(timer -> HttpEndpointMetrics.builder()
                        .uri(timer.getId().getTag("uri"))
                        .method(timer.getId().getTag("method"))
                        .status(timer.getId().getTag("status"))
                        .requestsCount(timer.count())
                        .requestsSum(timer.totalTime(TimeUnit.SECONDS))
                        .requestsMax(timer.max(TimeUnit.SECONDS))
                        .build())
                .toList();
    }

    private static boolean isInternalMonitoringUri(String uri) {
        return uri != null && uri.startsWith(INTERNAL_MONITORING_URI_PREFIX);
    }

    private List<HikariPoolMetrics> hikariPoolMetrics() {
        return tagValues("hikaricp.connections.active", "pool").stream()
                .map(poolName -> HikariPoolMetrics.builder()
                        .poolName(poolName)
                        .active(intGaugeValue("hikaricp.connections.active", "pool", poolName))
                        .idle(intGaugeValue("hikaricp.connections.idle", "pool", poolName))
                        .pending(intGaugeValue("hikaricp.connections.pending", "pool", poolName))
                        .max(intGaugeValue("hikaricp.connections.max", "pool", poolName))
                        .timeoutsTotal(counterValue("hikaricp.connections.timeout", "pool", poolName))
                        .build())
                .toList();
    }

    private List<ExecutorMetrics> executorMetrics() {
        return tagValues("executor.active", "name").stream()
                .map(name -> ExecutorMetrics.builder()
                        .name(name)
                        .active(intGaugeValue("executor.active", "name", name))
                        .max(intGaugeValue("executor.pool.max", "name", name))
                        .queuedTasks(intGaugeValue("executor.queued", "name", name))
                        .queueRemaining(intGaugeValue("executor.queue.remaining", "name", name))
                        .build())
                .toList();
    }

    private Set<String> tagValues(String meterName, String tagKey) {
        return registry.find(meterName).gauges().stream()
                .map(gauge -> gauge.getId().getTag(tagKey))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Integer intGaugeValue(String name, String tagKey, String tagValue) {
        Gauge gauge = registry.find(name).tag(tagKey, tagValue).gauge();
        if (gauge == null) {
            return null;
        }
        double value = gauge.value();
        return Double.isNaN(value) ? null : (int) value;
    }

    private Long counterValue(String name, String tagKey, String tagValue) {
        Counter counter = registry.find(name).tag(tagKey, tagValue).counter();
        return counter != null ? (long) counter.count() : null;
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