package com.moni.domain.instance.constant;

public enum NodeExporterMetric {
    CPU_SECONDS_TOTAL("node_cpu_seconds_total"),

    MEM_TOTAL_BYTES("node_memory_MemTotal_bytes"),
    MEM_FREE_BYTES("node_memory_MemFree_bytes"),
    MEM_AVAILABLE_BYTES("node_memory_MemAvailable_bytes"),
    MEM_BUFFERS_BYTES("node_memory_Buffers_bytes"),
    MEM_CACHED_BYTES("node_memory_Cached_bytes"),
    MEM_SWAP_TOTAL_BYTES("node_memory_SwapTotal_bytes"),
    MEM_SWAP_FREE_BYTES("node_memory_SwapFree_bytes"),

    DISK_READS_TOTAL("node_disk_reads_completed_total"),
    DISK_WRITES_TOTAL("node_disk_writes_completed_total"),
    DISK_READ_BYTES_TOTAL("node_disk_read_bytes_total"),
    DISK_WRITTEN_BYTES_TOTAL("node_disk_written_bytes_total"),
    DISK_IO_TIME_SECONDS_TOTAL("node_disk_io_time_seconds_total"),

    FILESYSTEM_SIZE_BYTES("node_filesystem_size_bytes"),
    FILESYSTEM_AVAIL_BYTES("node_filesystem_avail_bytes"),

    NETWORK_RECEIVE_BYTES_TOTAL("node_network_receive_bytes_total"),
    NETWORK_TRANSMIT_BYTES_TOTAL("node_network_transmit_bytes_total"),
    NETWORK_RECEIVE_ERRORS_TOTAL("node_network_receive_errs_total"),
    NETWORK_TRANSMIT_ERRORS_TOTAL("node_network_transmit_errs_total");

    private final String wireName;

    NodeExporterMetric(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
