package com.moni.domain.instance.dto;

import lombok.Builder;

@Builder
public record InstanceMetrics(
        // CPU
        Double cpuSecondsTotal,
        Double cpuIOwaitSecondsTotal,

        // Memory
        Long memTotalBytes,
        Long memFreeBytes,
        Long memAvailableBytes,
        Long buffersBytes,
        Long cachedBytes,
        Long swapTotalBytes,
        Long swapFreeBytes,

        // Disk I/O
        Long diskReadsTotal,
        Long diskWritesTotal,
        Long diskReadBytesTotal,
        Long diskWrittenBytesTotal,
        Double diskIoTimeSecondsTotal,
        Long fsSizeBytes,
        Long fsAvailBytes,

        // Network
        Long netRxBytesTotal,
        Long netTxBytesTotal,
        Long netRxErrorsTotal,
        Long netTxErrorsTotal) {
}
