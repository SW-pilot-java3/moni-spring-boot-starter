package com.moni.domain.instance.dto.request;

import lombok.Builder;

@Builder
public record InstanceFilesystemMetrics(
        String mountPoint,
        Long fsSizeBytes,
        Long fsAvailBytes) {
}