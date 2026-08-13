package com.moni.domain.server.collector;

import com.moni.domain.server.dto.request.ServerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class ActuatorMetricsCollector implements ServerMetricsCollector {

    private final MeterRegistry registry;

    @Override
    public ServerMetrics collect() {
        try {
            return ServerMetrics.from(registry);
        } catch (Exception e) {
            log.warn("Failed to collect server metrics from MeterRegistry", e);
            return null;
        }
    }
}