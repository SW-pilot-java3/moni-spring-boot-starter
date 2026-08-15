package com.moni.domain.instance.dto.request;

import lombok.Builder;

@Builder
public record InstanceCpuMetrics(
        Integer coreId,
        Double cpuSecondsTotal,
        Double cpuIdleSecondsTotal,
        Double cpuIowaitSecondsTotal) {
}
