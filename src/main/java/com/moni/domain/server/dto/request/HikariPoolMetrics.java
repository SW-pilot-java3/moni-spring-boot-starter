package com.moni.domain.server.dto.request;

import lombok.Builder;

@Builder
public record HikariPoolMetrics(
        String poolName,
        Integer active,
        Integer idle,
        Integer pending,
        Integer max,
        Long timeoutsTotal) {
}
