package com.moni.domain.metric.push;

import com.moni.domain.metric.dto.request.MetricsPayload;
import com.moni.global.config.MoniProperties;
import java.net.http.HttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
public class MetricsSender {

    private static final String METRICS_PATH = "/api/v1/metrics";

    private final MoniProperties properties;
    private final RestClient restClient;

    public MetricsSender(MoniProperties properties) {
        this.properties = properties;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build());
        requestFactory.setReadTimeout(properties.getReadTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(properties.getServerUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public boolean send(MetricsPayload payload) {
        try {
            restClient.post()
                    .uri(METRICS_PATH)
                    .header("X-API-KEY", properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            log.warn("Failed to push metrics to {}{}: {}", properties.getServerUrl(), METRICS_PATH, e.getMessage());
            return false;
        }
    }
}