package com.moni.global.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
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
}
