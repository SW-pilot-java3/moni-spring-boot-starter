package com.moni.domain.instance.dto.request;

import lombok.Builder;

@Builder
public record InstanceDiskMetrics(
        String deviceName,
        Long readsTotal,
        Long writesTotal,
        Long readBytesTotal,
        Long writtenBytesTotal,
        Double ioTimeSecondsTotal) {
}