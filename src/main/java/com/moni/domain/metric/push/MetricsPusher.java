package com.moni.domain.metric.push;

import com.moni.domain.instance.collector.InstanceMetricsCollector;
import com.moni.domain.metric.dto.request.MetricsPayload;
import com.moni.domain.server.collector.ServerMetricsCollector;
import com.moni.global.config.MoniProperties;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class MetricsPusher {

    private final MoniProperties properties;
    private final ServerMetricsCollector serverMetricsCollector;
    private final InstanceMetricsCollector instanceMetricsCollector;
    private final MetricsSender sender;
    private final RetryQueue retryQueue;

    private ScheduledExecutorService executor;

    public void start() {
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "moni-metrics-pusher");
            thread.setDaemon(true);
            return thread;
        });
        long intervalMillis = properties.getInterval().toMillis();
        executor.scheduleWithFixedDelay(this::pushOnce, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
        log.info("Moni metrics pusher started, pushing every {}", properties.getInterval());
    }

    public void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void pushOnce() {
        try {
            retryQueue.offer(collectPayload());
            for (MetricsPayload payload : retryQueue.drainAll()) {
                if (!sender.send(payload)) {
                    retryQueue.offer(payload);
                }
            }
        } catch (Exception e) {
            log.warn("Moni metrics push cycle failed", e);
        }
    }

    private MetricsPayload collectPayload() {
        var server = serverMetricsCollector != null ? serverMetricsCollector.collect() : null;
        var instance = instanceMetricsCollector != null ? instanceMetricsCollector.collect() : null;
        return MetricsPayload.of(Instant.now(), server, instance);
    }
}
