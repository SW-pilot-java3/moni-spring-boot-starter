package com.moni.domain.server.collector;

import com.moni.domain.server.dto.request.ServerMetrics;

public interface ServerMetricsCollector {

    ServerMetrics collect();
}