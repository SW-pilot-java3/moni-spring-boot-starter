package com.moni.domain.server.collector;

import com.moni.domain.server.dto.ServerMetrics;

public interface ServerMetricsCollector {

    ServerMetrics collect();
}