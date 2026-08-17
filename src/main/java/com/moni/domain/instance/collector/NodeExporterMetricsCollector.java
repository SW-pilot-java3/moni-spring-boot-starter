package com.moni.domain.instance.collector;

import com.moni.domain.instance.constant.CpuMode;
import com.moni.domain.instance.constant.ExcludedInterface;
import com.moni.domain.instance.constant.NodeExporterLabel;
import com.moni.domain.instance.constant.NodeExporterMetric;
import com.moni.domain.instance.constant.PseudoFilesystemType;
import com.moni.domain.instance.dto.request.InstanceCpuMetrics;
import com.moni.domain.instance.dto.request.InstanceDiskMetrics;
import com.moni.domain.instance.dto.request.InstanceFilesystemMetrics;
import com.moni.domain.instance.dto.request.InstanceMetrics;
import com.moni.domain.instance.dto.request.InstanceNetworkMetrics;
import com.moni.global.config.MoniProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
public class NodeExporterMetricsCollector implements InstanceMetricsCollector {

    private static final Pattern LABEL_PATTERN = Pattern.compile("(\\w+)=\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern LOOP_DEVICE_PATTERN = Pattern.compile("loop\\d+");

    private final MoniProperties properties;
    private final RestClient restClient;

    public NodeExporterMetricsCollector(MoniProperties properties) {
        this.properties = properties;
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(properties.getConnectTimeout())
                .withReadTimeout(properties.getReadTimeout());
        this.restClient = RestClient.builder()
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
    }

    @Override
    public InstanceMetrics collect() {
        try {
            String body = restClient.get()
                    .uri(properties.getNodeExporterUrl())
                    .retrieve()
                    .body(String.class);
            return body == null ? null : parse(body);
        } catch (RestClientException e) {
            log.warn("Failed to scrape Node Exporter metrics from {}: {}",
                    properties.getNodeExporterUrl(), e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("Failed to collect instance metrics from Node Exporter", e);
            return null;
        }
    }

    InstanceMetrics parse(String body) {
        List<Sample> samples = parseSamples(body);
        return InstanceMetrics.builder()
                .cpuSecondsTotal(sumValues(samples, NodeExporterMetric.CPU_SECONDS_TOTAL))
                .cpuIdleSecondsTotal(sumValues(samples, NodeExporterMetric.CPU_SECONDS_TOTAL,
                        NodeExporterLabel.MODE, CpuMode.IDLE.value()))
                .cpuIowaitSecondsTotal(sumValues(samples, NodeExporterMetric.CPU_SECONDS_TOTAL,
                        NodeExporterLabel.MODE, CpuMode.IOWAIT.value()))
                .memTotalBytes(singleLongValue(samples, NodeExporterMetric.MEM_TOTAL_BYTES))
                .memFreeBytes(singleLongValue(samples, NodeExporterMetric.MEM_FREE_BYTES))
                .memAvailableBytes(singleLongValue(samples, NodeExporterMetric.MEM_AVAILABLE_BYTES))
                .buffersBytes(singleLongValue(samples, NodeExporterMetric.MEM_BUFFERS_BYTES))
                .cachedBytes(singleLongValue(samples, NodeExporterMetric.MEM_CACHED_BYTES))
                .swapTotalBytes(singleLongValue(samples, NodeExporterMetric.MEM_SWAP_TOTAL_BYTES))
                .swapFreeBytes(singleLongValue(samples, NodeExporterMetric.MEM_SWAP_FREE_BYTES))
                .cpus(cpuMetrics(samples))
                .disks(diskMetrics(samples))
                .filesystems(filesystemMetrics(samples))
                .networks(networkMetrics(samples))
                .build();
    }

    private List<InstanceCpuMetrics> cpuMetrics(List<Sample> samples) {
        return labelValues(samples, NodeExporterMetric.CPU_SECONDS_TOTAL, NodeExporterLabel.CPU).stream()
                .map(coreId -> InstanceCpuMetrics.builder()
                        .coreId(Integer.parseInt(coreId))
                        .cpuSecondsTotal(sumValues(samples, NodeExporterMetric.CPU_SECONDS_TOTAL,
                                NodeExporterLabel.CPU, coreId))
                        .cpuIdleSecondsTotal(valueFor(samples, NodeExporterMetric.CPU_SECONDS_TOTAL,
                                NodeExporterLabel.CPU, coreId, NodeExporterLabel.MODE, CpuMode.IDLE.value()))
                        .cpuIowaitSecondsTotal(valueFor(samples, NodeExporterMetric.CPU_SECONDS_TOTAL,
                                NodeExporterLabel.CPU, coreId, NodeExporterLabel.MODE, CpuMode.IOWAIT.value()))
                        .build())
                .toList();
    }

    private List<InstanceDiskMetrics> diskMetrics(List<Sample> samples) {
        return labelValues(samples, NodeExporterMetric.DISK_READS_TOTAL, NodeExporterLabel.DEVICE).stream()
                .filter(device -> !LOOP_DEVICE_PATTERN.matcher(device).matches())
                .map(device -> InstanceDiskMetrics.builder()
                        .deviceName(device)
                        .readsTotal(longValueFor(samples, NodeExporterMetric.DISK_READS_TOTAL,
                                NodeExporterLabel.DEVICE, device))
                        .writesTotal(longValueFor(samples, NodeExporterMetric.DISK_WRITES_TOTAL,
                                NodeExporterLabel.DEVICE, device))
                        .readBytesTotal(longValueFor(samples, NodeExporterMetric.DISK_READ_BYTES_TOTAL,
                                NodeExporterLabel.DEVICE, device))
                        .writtenBytesTotal(longValueFor(samples, NodeExporterMetric.DISK_WRITTEN_BYTES_TOTAL,
                                NodeExporterLabel.DEVICE, device))
                        .ioTimeSecondsTotal(valueFor(samples, NodeExporterMetric.DISK_IO_TIME_SECONDS_TOTAL,
                                NodeExporterLabel.DEVICE, device))
                        .build())
                .toList();
    }

    private List<InstanceFilesystemMetrics> filesystemMetrics(List<Sample> samples) {
        return samples.stream()
                .filter(sample -> sample.name().equals(NodeExporterMetric.FILESYSTEM_SIZE_BYTES.wireName()))
                .filter(sample -> !PseudoFilesystemType.isPseudo(sample.labels().get(NodeExporterLabel.FSTYPE.key())))
                .map(sample -> {
                    String mountPoint = sample.labels().get(NodeExporterLabel.MOUNTPOINT.key());
                    return InstanceFilesystemMetrics.builder()
                            .mountPoint(mountPoint)
                            .fsSizeBytes((long) sample.value())
                            .fsAvailBytes(longValueFor(samples, NodeExporterMetric.FILESYSTEM_AVAIL_BYTES,
                                    NodeExporterLabel.MOUNTPOINT, mountPoint))
                            .build();
                })
                .toList();
    }

    private List<InstanceNetworkMetrics> networkMetrics(List<Sample> samples) {
        return labelValues(samples, NodeExporterMetric.NETWORK_RECEIVE_BYTES_TOTAL, NodeExporterLabel.DEVICE).stream()
                .filter(iface -> !ExcludedInterface.isExcluded(iface))
                .map(iface -> InstanceNetworkMetrics.builder()
                        .interfaceName(iface)
                        .rxBytesTotal(longValueFor(samples, NodeExporterMetric.NETWORK_RECEIVE_BYTES_TOTAL,
                                NodeExporterLabel.DEVICE, iface))
                        .txBytesTotal(longValueFor(samples, NodeExporterMetric.NETWORK_TRANSMIT_BYTES_TOTAL,
                                NodeExporterLabel.DEVICE, iface))
                        .rxErrorsTotal(longValueFor(samples, NodeExporterMetric.NETWORK_RECEIVE_ERRORS_TOTAL,
                                NodeExporterLabel.DEVICE, iface))
                        .txErrorsTotal(longValueFor(samples, NodeExporterMetric.NETWORK_TRANSMIT_ERRORS_TOTAL,
                                NodeExporterLabel.DEVICE, iface))
                        .build())
                .toList();
    }

    private Set<String> labelValues(List<Sample> samples, NodeExporterMetric metric, NodeExporterLabel labelKey) {
        return samples.stream()
                .filter(sample -> sample.name().equals(metric.wireName()))
                .map(sample -> sample.labels().get(labelKey.key()))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Double sumValues(List<Sample> samples, NodeExporterMetric metric) {
        List<Double> values = samples.stream()
                .filter(sample -> sample.name().equals(metric.wireName()))
                .map(Sample::value)
                .toList();
        return values.isEmpty() ? null : values.stream().mapToDouble(Double::doubleValue).sum();
    }

    private Double sumValues(List<Sample> samples, NodeExporterMetric metric, NodeExporterLabel labelKey,
            String labelValue) {
        List<Double> values = samples.stream()
                .filter(sample -> sample.name().equals(metric.wireName())
                        && labelValue.equals(sample.labels().get(labelKey.key())))
                .map(Sample::value)
                .toList();
        return values.isEmpty() ? null : values.stream().mapToDouble(Double::doubleValue).sum();
    }

    private Long singleLongValue(List<Sample> samples, NodeExporterMetric metric) {
        return samples.stream()
                .filter(sample -> sample.name().equals(metric.wireName()))
                .findFirst()
                .map(sample -> (long) sample.value())
                .orElse(null);
    }

    private Long longValueFor(List<Sample> samples, NodeExporterMetric metric, NodeExporterLabel labelKey,
            String labelValue) {
        return samples.stream()
                .filter(sample -> sample.name().equals(metric.wireName())
                        && labelValue.equals(sample.labels().get(labelKey.key())))
                .findFirst()
                .map(sample -> (long) sample.value())
                .orElse(null);
    }

    private Double valueFor(List<Sample> samples, NodeExporterMetric metric, NodeExporterLabel labelKey,
            String labelValue) {
        return samples.stream()
                .filter(sample -> sample.name().equals(metric.wireName())
                        && labelValue.equals(sample.labels().get(labelKey.key())))
                .findFirst()
                .map(Sample::value)
                .orElse(null);
    }

    private Double valueFor(List<Sample> samples, NodeExporterMetric metric, NodeExporterLabel labelKey1,
            String labelValue1, NodeExporterLabel labelKey2, String labelValue2) {
        return samples.stream()
                .filter(sample -> sample.name().equals(metric.wireName())
                        && labelValue1.equals(sample.labels().get(labelKey1.key()))
                        && labelValue2.equals(sample.labels().get(labelKey2.key())))
                .findFirst()
                .map(Sample::value)
                .orElse(null);
    }

    private List<Sample> parseSamples(String body) {
        List<Sample> samples = new ArrayList<>();
        for (String rawLine : body.split("\n")) {
            String line = rawLine.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            Sample sample = parseLine(line);
            if (sample != null) {
                samples.add(sample);
            }
        }
        return samples;
    }

    private Sample parseLine(String line) {
        String name;
        Map<String, String> labels;
        String rest;

        int braceStart = line.indexOf('{');
        if (braceStart >= 0) {
            int braceEnd = line.indexOf('}', braceStart);
            if (braceEnd < 0) {
                return null;
            }
            name = line.substring(0, braceStart);
            labels = parseLabels(line.substring(braceStart + 1, braceEnd));
            rest = line.substring(braceEnd + 1).strip();
        } else {
            int spaceIdx = line.indexOf(' ');
            if (spaceIdx < 0) {
                return null;
            }
            name = line.substring(0, spaceIdx);
            labels = Map.of();
            rest = line.substring(spaceIdx + 1).strip();
        }

        String valueToken = rest.split("\\s+")[0];
        try {
            return new Sample(name, labels, Double.parseDouble(valueToken));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<String, String> parseLabels(String raw) {
        Map<String, String> labels = new LinkedHashMap<>();
        Matcher matcher = LABEL_PATTERN.matcher(raw);
        while (matcher.find()) {
            labels.put(matcher.group(1), matcher.group(2));
        }
        return labels;
    }

    private record Sample(String name, Map<String, String> labels, double value) {
    }
}
