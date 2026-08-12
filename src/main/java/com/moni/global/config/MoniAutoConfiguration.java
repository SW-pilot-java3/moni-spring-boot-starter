package com.moni.global.config;

import com.moni.domain.instance.collector.InstanceMetricsCollector;
import com.moni.domain.metric.push.MetricsPusher;
import com.moni.domain.metric.push.MetricsSender;
import com.moni.domain.metric.push.RetryQueue;
import com.moni.domain.server.collector.ServerMetricsCollector;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

@AutoConfiguration
@EnableConfigurationProperties(MoniProperties.class)
@ConditionalOnProperty(prefix = "moni", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MoniAutoConfiguration {

    public MoniAutoConfiguration(MoniProperties properties) {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new IllegalStateException("moni.api-key must be set to enable Moni monitoring");
        }
        if (!StringUtils.hasText(properties.getServerUrl())) {
            throw new IllegalStateException("moni.server-url must be set to enable Moni monitoring");
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public MetricsSender moniMetricsSender(MoniProperties properties) {
        return new MetricsSender(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public RetryQueue moniRetryQueue(MoniProperties properties) {
        return new RetryQueue(properties.getRetryQueueSize());
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    public MetricsPusher moniMetricsPusher(MoniProperties properties,
            ObjectProvider<ServerMetricsCollector> serverMetricsCollector,
            ObjectProvider<InstanceMetricsCollector> instanceMetricsCollector,
            MetricsSender moniMetricsSender, RetryQueue moniRetryQueue) {
        return new MetricsPusher(properties, serverMetricsCollector.getIfAvailable(),
                instanceMetricsCollector.getIfAvailable(), moniMetricsSender, moniRetryQueue);
    }
}
