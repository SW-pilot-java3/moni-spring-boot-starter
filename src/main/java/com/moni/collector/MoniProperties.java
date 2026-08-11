package com.moni.collector;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "moni")
public class MoniProperties {

    private String apiKey;

    private String serverUrl;

    private boolean enabled = true;

    private Duration interval = Duration.ofSeconds(10);

    private String nodeExporterUrl = "http://localhost:9100/metrics";

    private boolean collectHostMetrics = true;

    private int retryQueueSize = 20;

    private Duration connectTimeout = Duration.ofSeconds(2);

    private Duration readTimeout = Duration.ofSeconds(5);

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getInterval() {
        return interval;
    }

    public void setInterval(Duration interval) {
        this.interval = interval;
    }

    public String getNodeExporterUrl() {
        return nodeExporterUrl;
    }

    public void setNodeExporterUrl(String nodeExporterUrl) {
        this.nodeExporterUrl = nodeExporterUrl;
    }

    public boolean isCollectHostMetrics() {
        return collectHostMetrics;
    }

    public void setCollectHostMetrics(boolean collectHostMetrics) {
        this.collectHostMetrics = collectHostMetrics;
    }

    public int getRetryQueueSize() {
        return retryQueueSize;
    }

    public void setRetryQueueSize(int retryQueueSize) {
        this.retryQueueSize = retryQueueSize;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }
}