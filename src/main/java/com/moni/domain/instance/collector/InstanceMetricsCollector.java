package com.moni.domain.instance.collector;

import com.moni.domain.instance.dto.InstanceMetrics;

public interface InstanceMetricsCollector {

    InstanceMetrics collect();
}
