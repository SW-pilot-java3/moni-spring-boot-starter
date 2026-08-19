package com.moni.domain.metric.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.moni.domain.metric.dto.request.MetricsPayload;
import com.moni.global.config.MoniProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
public class MetricsSender {

    private static final String METRICS_PATH = "/api/v1/metrics";

    private final MoniProperties properties;
    private final RestClient restClient;

    public MetricsSender(MoniProperties properties) {
        this.properties = properties;
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(properties.getConnectTimeout())
                .withReadTimeout(properties.getReadTimeout());
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        this.restClient = RestClient.builder()
                .baseUrl(properties.getServerUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .messageConverters(converters -> converters.stream()
                        .filter(MappingJackson2HttpMessageConverter.class::isInstance)
                        .map(MappingJackson2HttpMessageConverter.class::cast)
                        .forEach(converter -> converter.setObjectMapper(objectMapper)))
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