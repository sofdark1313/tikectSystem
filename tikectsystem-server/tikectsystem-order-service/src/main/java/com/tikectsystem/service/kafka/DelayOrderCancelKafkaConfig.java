package com.tikectsystem.service.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * 过期订单取消专用Kafka消费配置，避免复用创建订单消费组。
 */
@Configuration
public class DelayOrderCancelKafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:127.0.0.1:9092}")
    private String bootstrapServers;

    @Value("${prefix.distinction.name:tikectsystem}")
    private String prefixDistinctionName;

    @Value("${delay.order.cancel.kafka.group-id:delay_order_cancel_data}")
    private String groupId;

    @Value("${delay.order.cancel.kafka.concurrency:4}")
    private Integer concurrency;

    @Value("${delay.order.cancel.kafka.max-poll-records:500}")
    private Integer maxPollRecords;

    private DefaultKafkaConsumerFactory<String, String> delayOrderCancelKafkaConsumerFactory() {
        Map<String, Object> properties = new HashMap<>(8);
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, prefixDistinctionName + "-" + groupId);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        properties.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);
        return new DefaultKafkaConsumerFactory<>(properties);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> delayOrderCancelKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(delayOrderCancelKafkaConsumerFactory());
        factory.setConcurrency(Math.max(1, concurrency));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }
}
