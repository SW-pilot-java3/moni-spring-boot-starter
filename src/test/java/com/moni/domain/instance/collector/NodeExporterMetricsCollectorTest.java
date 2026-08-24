package com.moni.domain.instance.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import com.moni.domain.instance.dto.request.InstanceCpuMetrics;
import com.moni.domain.instance.dto.request.InstanceDiskMetrics;
import com.moni.domain.instance.dto.request.InstanceFilesystemMetrics;
import com.moni.domain.instance.dto.request.InstanceMetrics;
import com.moni.domain.instance.dto.request.InstanceNetworkMetrics;
import com.moni.global.config.MoniProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class NodeExporterMetricsCollectorTest {

    private static final String SAMPLE_BODY = """
            # HELP node_cpu_seconds_total Seconds the CPUs spent in each mode.
            # TYPE node_cpu_seconds_total counter
            node_cpu_seconds_total{cpu="0",mode="idle"} 1500.5
            node_cpu_seconds_total{cpu="0",mode="iowait"} 12.5
            node_cpu_seconds_total{cpu="0",mode="user"} 300.0
            node_cpu_seconds_total{cpu="1",mode="idle"} 1600.0
            node_cpu_seconds_total{cpu="1",mode="iowait"} 8.0
            node_cpu_seconds_total{cpu="1",mode="user"} 250.0

            node_memory_MemTotal_bytes 8.589934592e+09
            node_memory_MemFree_bytes 1.073741824e+09
            node_memory_MemAvailable_bytes 3.221225472e+09
            node_memory_Buffers_bytes 1.048576e+08
            node_memory_Cached_bytes 2.147483648e+09
            node_memory_SwapTotal_bytes 2.147483648e+09
            node_memory_SwapFree_bytes 2.147483648e+09

            node_disk_reads_completed_total{device="nvme0n1"} 182345
            node_disk_reads_completed_total{device="nvme1n1"} 45210
            node_disk_reads_completed_total{device="loop0"} 12
            node_disk_reads_completed_total{device="dm-0"} 34
            node_disk_reads_completed_total{device="ram0"} 56
            node_disk_writes_completed_total{device="nvme0n1"} 94021
            node_disk_writes_completed_total{device="nvme1n1"} 128900
            node_disk_writes_completed_total{device="loop0"} 0
            node_disk_writes_completed_total{device="dm-0"} 0
            node_disk_writes_completed_total{device="ram0"} 0
            node_disk_read_bytes_total{device="nvme0n1"} 5872345088
            node_disk_read_bytes_total{device="nvme1n1"} 943718400
            node_disk_written_bytes_total{device="nvme0n1"} 2103450624
            node_disk_written_bytes_total{device="nvme1n1"} 6710886400
            node_disk_io_time_seconds_total{device="nvme0n1"} 812.4
            node_disk_io_time_seconds_total{device="nvme1n1"} 2140.7

            node_filesystem_size_bytes{device="/dev/nvme0n1p1",fstype="ext4",mountpoint="/"} 21474836480
            node_filesystem_avail_bytes{device="/dev/nvme0n1p1",fstype="ext4",mountpoint="/"} 8589934592
            node_filesystem_size_bytes{device="/dev/nvme1n1",fstype="xfs",mountpoint="/data"} 107374182400
            node_filesystem_avail_bytes{device="/dev/nvme1n1",fstype="xfs",mountpoint="/data"} 5368709120
            node_filesystem_size_bytes{device="tmpfs",fstype="tmpfs",mountpoint="/run"} 838860800
            node_filesystem_avail_bytes{device="tmpfs",fstype="tmpfs",mountpoint="/run"} 838860800

            node_network_receive_bytes_total{device="eth0"} 48234567890
            node_network_transmit_bytes_total{device="eth0"} 12345678901
            node_network_receive_errs_total{device="eth0"} 0
            node_network_transmit_errs_total{device="eth0"} 0
            node_network_receive_bytes_total{device="lo"} 982345678
            node_network_transmit_bytes_total{device="lo"} 982345678
            node_network_receive_errs_total{device="lo"} 0
            node_network_transmit_errs_total{device="lo"} 0
            node_network_receive_bytes_total{device="docker0"} 123456
            node_network_transmit_bytes_total{device="docker0"} 123456
            node_network_receive_errs_total{device="docker0"} 0
            node_network_transmit_errs_total{device="docker0"} 0
            node_network_receive_bytes_total{device="veth3a1f9c2"} 7890
            node_network_transmit_bytes_total{device="veth3a1f9c2"} 7890
            node_network_receive_errs_total{device="veth3a1f9c2"} 0
            node_network_transmit_errs_total{device="veth3a1f9c2"} 0
            """;

    private final NodeExporterMetricsCollector collector = new NodeExporterMetricsCollector(new MoniProperties());

    @Test
    void parsesCpuAcrossAllCoresAndModes() {
        InstanceMetrics metrics = collector.parse(SAMPLE_BODY);

        assertThat(metrics.cpuSecondsTotal()).isCloseTo(3671.0, offset(0.001));
        assertThat(metrics.cpuIdleSecondsTotal()).isCloseTo(3100.5, offset(0.001));
        assertThat(metrics.cpuIowaitSecondsTotal()).isCloseTo(20.5, offset(0.001));
    }

    @Test
    void groupsCpuMetricsByCore() {
        InstanceMetrics metrics = collector.parse(SAMPLE_BODY);

        assertThat(metrics.cpus())
                .extracting(InstanceCpuMetrics::coreId)
                .containsExactlyInAnyOrder(0, 1);
        assertThat(metrics.cpus())
                .filteredOn(cpu -> cpu.coreId() == 0)
                .singleElement()
                .satisfies(cpu -> {
                    assertThat(cpu.cpuSecondsTotal()).isCloseTo(1813.0, offset(0.001));
                    assertThat(cpu.cpuIdleSecondsTotal()).isCloseTo(1500.5, offset(0.001));
                    assertThat(cpu.cpuIowaitSecondsTotal()).isCloseTo(12.5, offset(0.001));
                });
        assertThat(metrics.cpus())
                .filteredOn(cpu -> cpu.coreId() == 1)
                .singleElement()
                .satisfies(cpu -> {
                    assertThat(cpu.cpuSecondsTotal()).isCloseTo(1858.0, offset(0.001));
                    assertThat(cpu.cpuIdleSecondsTotal()).isCloseTo(1600.0, offset(0.001));
                    assertThat(cpu.cpuIowaitSecondsTotal()).isCloseTo(8.0, offset(0.001));
                });
    }

    @Test
    void parsesMemoryScalarsIncludingScientificNotation() {
        InstanceMetrics metrics = collector.parse(SAMPLE_BODY);

        assertThat(metrics.memTotalBytes()).isEqualTo(8_589_934_592L);
        assertThat(metrics.memFreeBytes()).isEqualTo(1_073_741_824L);
        assertThat(metrics.memAvailableBytes()).isEqualTo(3_221_225_472L);
        assertThat(metrics.buffersBytes()).isEqualTo(104_857_600L);
        assertThat(metrics.cachedBytes()).isEqualTo(2_147_483_648L);
        assertThat(metrics.swapTotalBytes()).isEqualTo(2_147_483_648L);
        assertThat(metrics.swapFreeBytes()).isEqualTo(2_147_483_648L);
    }

    @Test
    void groupsDiskMetricsByDeviceAndExcludesVirtualDevices() {
        InstanceMetrics metrics = collector.parse(SAMPLE_BODY);

        assertThat(metrics.disks())
                .extracting(InstanceDiskMetrics::deviceName)
                .containsExactlyInAnyOrder("nvme0n1", "nvme1n1");
        assertThat(metrics.disks())
                .filteredOn(disk -> disk.deviceName().equals("nvme1n1"))
                .singleElement()
                .satisfies(disk -> {
                    assertThat(disk.readsTotal()).isEqualTo(45210L);
                    assertThat(disk.writesTotal()).isEqualTo(128900L);
                    assertThat(disk.readBytesTotal()).isEqualTo(943718400L);
                    assertThat(disk.writtenBytesTotal()).isEqualTo(6710886400L);
                    assertThat(disk.ioTimeSecondsTotal()).isCloseTo(2140.7, offset(0.001));
                });
    }

    @Test
    void groupsFilesystemMetricsByMountpointAndExcludesPseudoFilesystems() {
        InstanceMetrics metrics = collector.parse(SAMPLE_BODY);

        assertThat(metrics.filesystems())
                .extracting(InstanceFilesystemMetrics::mountPoint)
                .containsExactlyInAnyOrder("/", "/data");
        assertThat(metrics.filesystems())
                .filteredOn(fs -> fs.mountPoint().equals("/data"))
                .singleElement()
                .satisfies(fs -> {
                    assertThat(fs.fsSizeBytes()).isEqualTo(107374182400L);
                    assertThat(fs.fsAvailBytes()).isEqualTo(5368709120L);
                });
    }

    @Test
    void groupsNetworkMetricsByInterfaceAndExcludesVirtualInterfaces() {
        InstanceMetrics metrics = collector.parse(SAMPLE_BODY);

        assertThat(metrics.networks())
                .extracting(InstanceNetworkMetrics::interfaceName)
                .containsExactly("eth0");
        assertThat(metrics.networks())
                .singleElement()
                .satisfies(net -> {
                    assertThat(net.rxBytesTotal()).isEqualTo(48234567890L);
                    assertThat(net.txBytesTotal()).isEqualTo(12345678901L);
                    assertThat(net.rxErrorsTotal()).isEqualTo(0L);
                    assertThat(net.txErrorsTotal()).isEqualTo(0L);
                });
    }

    @Test
    void ignoresCommentsAndBlankLines() {
        InstanceMetrics metrics = collector.parse("""
                # HELP ignored
                # TYPE ignored counter

                node_memory_MemTotal_bytes 1024
                """);

        assertThat(metrics.memTotalBytes()).isEqualTo(1024L);
    }

    @Test
    void returnsNullInsteadOfThrowingWhenNodeExporterIsUnreachable() {
        MoniProperties properties = new MoniProperties();
        properties.setNodeExporterUrl("http://localhost:1/metrics");
        properties.setConnectTimeout(Duration.ofMillis(200));
        properties.setReadTimeout(Duration.ofMillis(200));
        NodeExporterMetricsCollector unreachableCollector = new NodeExporterMetricsCollector(properties);

        InstanceMetrics metrics = unreachableCollector.collect();

        assertThat(metrics).isNull();
    }
}