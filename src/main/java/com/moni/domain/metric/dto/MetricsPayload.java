package com.moni.domain.metric.dto;

import com.moni.domain.instance.dto.InstanceMetrics;
import com.moni.domain.server.dto.ServerMetrics;
import java.time.Instant;

public record MetricsPayload(Instant collectedAt, ServerMetrics server, InstanceMetrics instance) {

    public static MetricsPayload of(Instant collectedAt, ServerMetrics server, InstanceMetrics instance) {
        return new MetricsPayload(collectedAt, server, instance);
    }
}
