package com.moni.domain.instance.collector;

import com.moni.domain.instance.dto.request.InstanceMetrics;

public interface InstanceMetricsCollector {

    InstanceMetrics collect();
}
