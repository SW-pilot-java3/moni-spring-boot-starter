package com.moni.domain.server.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.moni.domain.server.dto.request.ServerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
}