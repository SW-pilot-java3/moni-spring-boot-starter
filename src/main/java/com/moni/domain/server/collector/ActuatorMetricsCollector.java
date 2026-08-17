package com.moni.domain.server.collector;

import com.moni.domain.server.constant.MemoryArea;
import com.moni.domain.server.constant.MicrometerMeter;
import com.moni.domain.server.constant.MicrometerTag;
import com.moni.domain.server.constant.OldGenPoolMarker;
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
                .processUptimeSeconds(sumGaugeValue(MicrometerMeter.PROCESS_UPTIME))
                .jvmThreadsLive(sumIntGaugeValue(MicrometerMeter.JVM_THREADS_LIVE))
                .jvmThreadsBlocked(sumIntGaugeValue(MicrometerMeter.JVM_THREADS_STATES,
                        MicrometerTag.STATE.key(), "blocked"))
                .httpEndpoints(httpEndpointMetrics())
                .hikaricpPools(hikariPoolMetrics())
                .executors(executorMetrics())
                .build();
    }

    private List<HttpEndpointMetrics> httpEndpointMetrics() {
        return registry.find(MicrometerMeter.HTTP_SERVER_REQUESTS.meterName()).timers().stream()
                .filter(timer -> !isInternalMonitoringUri(timer.getId().getTag(MicrometerTag.URI.key())))
                .map(timer -> HttpEndpointMetrics.builder()
                        .uri(timer.getId().getTag(MicrometerTag.URI.key()))
                        .method(timer.getId().getTag(MicrometerTag.METHOD.key()))
                        .status(timer.getId().getTag(MicrometerTag.STATUS.key()))
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
        return tagValues(MicrometerMeter.HIKARICP_CONNECTIONS_ACTIVE, MicrometerTag.POOL).stream()
                .map(poolName -> HikariPoolMetrics.builder()
                        .poolName(poolName)
                        .active(intGaugeValue(MicrometerMeter.HIKARICP_CONNECTIONS_ACTIVE, MicrometerTag.POOL, poolName))
                        .idle(intGaugeValue(MicrometerMeter.HIKARICP_CONNECTIONS_IDLE, MicrometerTag.POOL, poolName))
                        .pending(intGaugeValue(MicrometerMeter.HIKARICP_CONNECTIONS_PENDING, MicrometerTag.POOL, poolName))
                        .max(intGaugeValue(MicrometerMeter.HIKARICP_CONNECTIONS_MAX, MicrometerTag.POOL, poolName))
                        .timeoutsTotal(counterValue(MicrometerMeter.HIKARICP_CONNECTIONS_TIMEOUT, MicrometerTag.POOL, poolName))
                        .build())
                .toList();
    }

    private List<ExecutorMetrics> executorMetrics() {
        return tagValues(MicrometerMeter.EXECUTOR_ACTIVE, MicrometerTag.NAME).stream()
                .map(name -> ExecutorMetrics.builder()
                        .name(name)
                        .active(intGaugeValue(MicrometerMeter.EXECUTOR_ACTIVE, MicrometerTag.NAME, name))
                        .max(intGaugeValue(MicrometerMeter.EXECUTOR_POOL_MAX, MicrometerTag.NAME, name))
                        .queuedTasks(intGaugeValue(MicrometerMeter.EXECUTOR_QUEUED, MicrometerTag.NAME, name))
                        .queueRemaining(intGaugeValue(MicrometerMeter.EXECUTOR_QUEUE_REMAINING, MicrometerTag.NAME, name))
                        .build())
                .toList();
    }

    private Set<String> tagValues(MicrometerMeter meter, MicrometerTag tag) {
        return registry.find(meter.meterName()).gauges().stream()
                .map(gauge -> gauge.getId().getTag(tag.key()))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Integer intGaugeValue(MicrometerMeter meter, MicrometerTag tag, String tagValue) {
        Gauge gauge = registry.find(meter.meterName()).tag(tag.key(), tagValue).gauge();
        if (gauge == null) {
            return null;
        }
        double value = gauge.value();
        return Double.isNaN(value) ? null : (int) value;
    }

    private Long counterValue(MicrometerMeter meter, MicrometerTag tag, String tagValue) {
        Counter counter = registry.find(meter.meterName()).tag(tag.key(), tagValue).counter();
        return counter != null ? (long) counter.count() : null;
    }

    private Long oldGenUsedBytes() {
        return registry.find(MicrometerMeter.JVM_MEMORY_USED.meterName())
                .tag(MicrometerTag.AREA.key(), MemoryArea.HEAP.value())
                .gauges().stream()
                .filter(gauge -> OldGenPoolMarker.matchesPoolId(gauge.getId().getTag(MicrometerTag.ID.key())))
                .findFirst()
                .map(Gauge::value)
                .filter(value -> !Double.isNaN(value))
                .map(value -> (long) (double) value)
                .orElse(null);
    }

    private Long gcPauseSecondsCount() {
        Collection<Timer> timers = registry.find(MicrometerMeter.JVM_GC_PAUSE.meterName()).timers();
        return timers.isEmpty() ? null : timers.stream().mapToLong(Timer::count).sum();
    }

    private Double gcPauseSecondsSum() {
        Collection<Timer> timers = registry.find(MicrometerMeter.JVM_GC_PAUSE.meterName()).timers();
        return timers.isEmpty() ? null
                : timers.stream().mapToDouble(timer -> timer.totalTime(TimeUnit.SECONDS)).sum();
    }

    private Long sumGauges() {
        Collection<Gauge> gauges = registry.find(MicrometerMeter.JVM_MEMORY_USED.meterName())
                .tag(MicrometerTag.AREA.key(), MemoryArea.HEAP.value())
                .gauges();
        if (gauges.isEmpty()) {
            return null;
        }
        return (long) gauges.stream().mapToDouble(Gauge::value).sum();
    }

    private Long sumPositiveGauges() {
        Collection<Gauge> gauges = registry.find(MicrometerMeter.JVM_MEMORY_MAX.meterName())
                .tag(MicrometerTag.AREA.key(), MemoryArea.HEAP.value())
                .gauges();
        if (gauges.isEmpty()) {
            return null;
        }
        double sum = gauges.stream().mapToDouble(Gauge::value).filter(value -> value >= 0).sum();
        return (long) sum;
    }

    private Double sumGaugeValue(MicrometerMeter meter, String... tags) {
        List<Double> values = registry.find(meter.meterName()).tags(tags).gauges().stream()
                .map(Gauge::value)
                .filter(value -> !Double.isNaN(value))
                .toList();
        return values.isEmpty() ? null : values.stream().mapToDouble(Double::doubleValue).sum();
    }

    private Integer sumIntGaugeValue(MicrometerMeter meter, String... tags) {
        Double value = sumGaugeValue(meter, tags);
        return value != null ? (int) (double) value : null;
    }
}
