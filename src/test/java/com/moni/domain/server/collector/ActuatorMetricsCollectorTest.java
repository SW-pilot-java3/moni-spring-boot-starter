package com.moni.domain.server.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.moni.domain.server.dto.request.HikariPoolMetrics;
import com.moni.domain.server.dto.request.ServerMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ActuatorMetricsCollectorTest {

    @Test
    void collectsServerMetricsFromRegistry() {
        MeterRegistry registry = new SimpleMeterRegistry();
        registry.gauge("jvm.threads.live", 10.0);
        ActuatorMetricsCollector collector = new ActuatorMetricsCollector(registry);

        ServerMetrics metrics = collector.collect();

        assertThat(metrics).isNotNull();
        assertThat(metrics.jvmThreadsLive()).isEqualTo(10);
    }

    @Test
    void returnsNullInsteadOfThrowingWhenRegistryLookupFails() {
        MeterRegistry registry = mock(MeterRegistry.class);
        when(registry.find(anyString())).thenThrow(new RuntimeException("registry lookup failed"));
        ActuatorMetricsCollector collector = new ActuatorMetricsCollector(registry);

        ServerMetrics metrics = collector.collect();

        assertThat(metrics).isNull();
    }

    @Test
    void returnsAllNullFieldsWhenRegistryHasNoMeters() {
        MeterRegistry registry = new SimpleMeterRegistry();

        ServerMetrics metrics = new ActuatorMetricsCollector(registry).collect();

        assertThat(metrics.jvmHeapUsedBytes()).isNull();
        assertThat(metrics.jvmHeapMaxBytes()).isNull();
        assertThat(metrics.jvmOldGenUsedBytes()).isNull();
        assertThat(metrics.gcPauseSecondsCount()).isNull();
        assertThat(metrics.gcPauseSecondsSum()).isNull();
        assertThat(metrics.processUptimeSeconds()).isNull();
        assertThat(metrics.jvmThreadsLive()).isNull();
        assertThat(metrics.jvmThreadsBlocked()).isNull();
        assertThat(metrics.httpRequestsCount()).isNull();
        assertThat(metrics.httpRequestsSum()).isNull();
        assertThat(metrics.httpRequestsMax()).isNull();
        assertThat(metrics.httpErrorsCount()).isNull();
        assertThat(metrics.tomcatThreadsBusy()).isNull();
        assertThat(metrics.tomcatThreadsConfigMax()).isNull();
        assertThat(metrics.hikaricpPools()).isEmpty();
        assertThat(metrics.executorActive()).isNull();
        assertThat(metrics.executorMax()).isNull();
        assertThat(metrics.executorQueuedTasks()).isNull();
        assertThat(metrics.executorQueueRemaining()).isNull();
    }

    @Test
    void sumsHeapMemoryPoolsAndExcludesUnboundedMaxAndNonHeapPools() {
        MeterRegistry registry = new SimpleMeterRegistry();
        registerMemoryPool(registry, "heap", "G1 Eden Space", 100.0, -1.0);
        registerMemoryPool(registry, "heap", "G1 Old Gen", 50.0, 200.0);
        registerMemoryPool(registry, "nonheap", "Metaspace", 9999.0, 9999.0);

        ServerMetrics metrics = new ActuatorMetricsCollector(registry).collect();

        assertThat(metrics.jvmHeapUsedBytes()).isEqualTo(150L);
        assertThat(metrics.jvmHeapMaxBytes()).isEqualTo(200L);
    }

    @Test
    void picksOldGenPoolByIdNameHeuristic() {
        MeterRegistry registry = new SimpleMeterRegistry();
        registerMemoryPool(registry, "heap", "G1 Eden Space", 100.0, 500.0);
        registerMemoryPool(registry, "heap", "G1 Old Gen", 77.0, 300.0);

        ServerMetrics metrics = new ActuatorMetricsCollector(registry).collect();

        assertThat(metrics.jvmOldGenUsedBytes()).isEqualTo(77L);
    }

    @Test
    void aggregatesGcPauseTimersAcrossActions() {
        MeterRegistry registry = new SimpleMeterRegistry();
        Timer youngGc = Timer.builder("jvm.gc.pause").tag("action", "end of minor GC").register(registry);
        Timer oldGc = Timer.builder("jvm.gc.pause").tag("action", "end of major GC").register(registry);
        youngGc.record(Duration.ofMillis(10));
        youngGc.record(Duration.ofMillis(20));
        oldGc.record(Duration.ofMillis(100));

        ServerMetrics metrics = new ActuatorMetricsCollector(registry).collect();

        assertThat(metrics.gcPauseSecondsCount()).isEqualTo(3L);
        assertThat(metrics.gcPauseSecondsSum()).isCloseTo(0.13, offset(0.001));
    }

    @Test
    void readsProcessUptimeAndThreadGauges() {
        MeterRegistry registry = new SimpleMeterRegistry();
        registry.gauge("process.uptime", 12345.0);
        registry.gauge("jvm.threads.live", 42.0);
        Gauge.builder("jvm.threads.states", () -> 3.0).tag("state", "blocked").register(registry);
        Gauge.builder("jvm.threads.states", () -> 10.0).tag("state", "runnable").register(registry);

        ServerMetrics metrics = new ActuatorMetricsCollector(registry).collect();

        assertThat(metrics.processUptimeSeconds()).isEqualTo(12345.0);
        assertThat(metrics.jvmThreadsLive()).isEqualTo(42);
        assertThat(metrics.jvmThreadsBlocked()).isEqualTo(3);
    }

    @Test
    void aggregatesHttpRequestTimersAcrossTagsAndCountsErrors() {
        MeterRegistry registry = new SimpleMeterRegistry();
        Timer ok = Timer.builder("http.server.requests").tag("status", "200").tag("uri", "/a").register(registry);
        Timer notFound = Timer.builder("http.server.requests")
                .tag("status", "404").tag("uri", "/b").register(registry);
        Timer serverError = Timer.builder("http.server.requests")
                .tag("status", "500").tag("uri", "/c").register(registry);
        ok.record(Duration.ofMillis(100));
        ok.record(Duration.ofMillis(300));
        notFound.record(Duration.ofMillis(50));
        serverError.record(Duration.ofMillis(900));

        ServerMetrics metrics = new ActuatorMetricsCollector(registry).collect();

        assertThat(metrics.httpRequestsCount()).isEqualTo(4L);
        assertThat(metrics.httpRequestsSum()).isCloseTo(1.35, offset(0.001));
        assertThat(metrics.httpRequestsMax()).isCloseTo(0.9, offset(0.001));
        assertThat(metrics.httpErrorsCount()).isEqualTo(2L);
    }

    @Test
    void readsExecutorAndTomcatMetersWhenPresent() {
        MeterRegistry registry = new SimpleMeterRegistry();
        registry.gauge("executor.active", 1.0);
        registry.gauge("executor.pool.max", 20.0);
        registry.gauge("executor.queued", 5.0);
        registry.gauge("executor.queue.remaining", 95.0);
        registry.gauge("tomcat.threads.busy", 4.0);
        registry.gauge("tomcat.threads.config.max", 200.0);

        ServerMetrics metrics = new ActuatorMetricsCollector(registry).collect();

        assertThat(metrics.executorActive()).isEqualTo(1);
        assertThat(metrics.executorMax()).isEqualTo(20);
        assertThat(metrics.executorQueuedTasks()).isEqualTo(5);
        assertThat(metrics.executorQueueRemaining()).isEqualTo(95);
        assertThat(metrics.tomcatThreadsBusy()).isEqualTo(4);
        assertThat(metrics.tomcatThreadsConfigMax()).isEqualTo(200);
    }

    @Test
    void groupsHikariPoolMetricsByPoolTagInsteadOfMergingThem() {
        MeterRegistry registry = new SimpleMeterRegistry();
        registerHikariPool(registry, "primaryPool", 5, 3, 0, 10, 0);
        registerHikariPool(registry, "readReplicaPool", 2, 8, 1, 10, 1);

        ServerMetrics metrics = new ActuatorMetricsCollector(registry).collect();

        assertThat(metrics.hikaricpPools())
                .extracting(HikariPoolMetrics::poolName)
                .containsExactlyInAnyOrder("primaryPool", "readReplicaPool");
        assertThat(metrics.hikaricpPools())
                .filteredOn(pool -> pool.poolName().equals("primaryPool"))
                .singleElement()
                .satisfies(pool -> {
                    assertThat(pool.active()).isEqualTo(5);
                    assertThat(pool.idle()).isEqualTo(3);
                    assertThat(pool.pending()).isEqualTo(0);
                    assertThat(pool.max()).isEqualTo(10);
                    assertThat(pool.timeoutsTotal()).isEqualTo(0L);
                });
        assertThat(metrics.hikaricpPools())
                .filteredOn(pool -> pool.poolName().equals("readReplicaPool"))
                .singleElement()
                .satisfies(pool -> {
                    assertThat(pool.active()).isEqualTo(2);
                    assertThat(pool.idle()).isEqualTo(8);
                    assertThat(pool.pending()).isEqualTo(1);
                    assertThat(pool.max()).isEqualTo(10);
                    assertThat(pool.timeoutsTotal()).isEqualTo(1L);
                });
    }

    private void registerMemoryPool(MeterRegistry registry, String area, String poolId, double used, double max) {
        Gauge.builder("jvm.memory.used", () -> used).tag("area", area).tag("id", poolId).register(registry);
        Gauge.builder("jvm.memory.max", () -> max).tag("area", area).tag("id", poolId).register(registry);
    }

    private void registerHikariPool(MeterRegistry registry, String poolName, double active, double idle,
            double pending, double max, long timeouts) {
        Gauge.builder("hikaricp.connections.active", () -> active).tag("pool", poolName).register(registry);
        Gauge.builder("hikaricp.connections.idle", () -> idle).tag("pool", poolName).register(registry);
        Gauge.builder("hikaricp.connections.pending", () -> pending).tag("pool", poolName).register(registry);
        Gauge.builder("hikaricp.connections.max", () -> max).tag("pool", poolName).register(registry);
        Counter.builder("hikaricp.connections.timeout").tag("pool", poolName).register(registry).increment(timeouts);
    }
}
