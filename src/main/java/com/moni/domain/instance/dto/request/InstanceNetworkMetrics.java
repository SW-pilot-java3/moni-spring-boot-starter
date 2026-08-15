package com.moni.domain.instance.dto.request;

import lombok.Builder;

@Builder
public record InstanceNetworkMetrics(
        String interfaceName,
        Long rxBytesTotal,
        Long txBytesTotal,
        Long rxErrorsTotal,
        Long txErrorsTotal) {
}