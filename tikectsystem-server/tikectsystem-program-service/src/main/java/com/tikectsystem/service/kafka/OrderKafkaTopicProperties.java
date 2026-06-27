package com.tikectsystem.service.kafka;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 下单链路 Kafka topic 配置。
 */
@Data
@Component
public class OrderKafkaTopicProperties {

    @Value("${spring.kafka.topic:create_order}")
    private String orderCreateTopic;

    @Value("${spring.kafka.order-request-topic:order_request}")
    private String orderRequestTopic;
}
