package com.moni.domain.instance.dto.request;

import java.util.List;
import lombok.Builder;

@Builder
public record InstanceMetrics(
        Double cpuSecondsTotal,
        Double cpuIdleSecondsTotal,
        Double cpuIOWaitSecondsTotal,

        Long memTotalBytes,
        Long memFreeBytes,
        Long memAvailableBytes,
        Long buffersBytes,
        Long cachedBytes,
        Long swapTotalBytes,
        Long swapFreeBytes,

        List<InstanceCpuMetrics> cpus,

        List<InstanceDiskMetrics> disks,

        List<InstanceFilesystemMetrics> filesystems,

        List<InstanceNetworkMetrics> networks) {
}
