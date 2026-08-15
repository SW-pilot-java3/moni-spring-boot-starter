package com.moni.domain.server.dto.request;

import lombok.Builder;

@Builder
public record ExecutorMetrics(
        String name,
        Integer active,
        Integer max,
        Integer queuedTasks,
        Integer queueRemaining) {
}