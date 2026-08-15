package com.moni.domain.server.dto.request;

import lombok.Builder;

@Builder
public record HttpEndpointMetrics(
        String uri,
        String method,
        String status,
        Long requestsCount,
        Double requestsSum,
        Double requestsMax) {
}
