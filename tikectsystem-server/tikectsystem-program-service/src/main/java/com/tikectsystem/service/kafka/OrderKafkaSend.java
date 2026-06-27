package com.tikectsystem.service.kafka;

import com.tikectsystem.core.SpringUtil;
import com.tikectsystem.mq.callback.FailureCallback;
import com.tikectsystem.mq.callback.SuccessCallback;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * 下单链路 Kafka 发送器。
 */
@Slf4j
@Component
public class OrderKafkaSend {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private OrderKafkaTopicProperties orderKafkaTopicProperties;

    private String orderRequestTopicName;

    private String orderCreateTopicName;

    @PostConstruct
    public void init() {
        orderRequestTopicName = SpringUtil.getPrefixDistinctionName() + "-" + orderKafkaTopicProperties.getOrderRequestTopic();
        orderCreateTopicName = SpringUtil.getPrefixDistinctionName() + "-" + orderKafkaTopicProperties.getOrderCreateTopic();
    }

    /**
     * 发送下单受理请求消息。
     * @param key 消息键
     * @param message 消息体
     * @param successCallback 成功回调
     * @param failureCallback 失败回调
     */
    public void sendOrderRequest(String key, String message, SuccessCallback<SendResult<String, String>> successCallback,
                                 FailureCallback failureCallback) {
        send(orderRequestTopicName, key, message, successCallback, failureCallback);
    }

    /**
     * 发送订单创建消息。
     * @param key 消息键
     * @param message 消息体
     * @param successCallback 成功回调
     * @param failureCallback 失败回调
     */
    public void sendOrderCreate(String key, String message, SuccessCallback<SendResult<String, String>> successCallback,
                                FailureCallback failureCallback) {
        send(orderCreateTopicName, key, message, successCallback, failureCallback);
    }

    private void send(String topic, String key, String message, SuccessCallback<SendResult<String, String>> successCallback,
                      FailureCallback failureCallback) {
        CompletableFuture<SendResult<String, String>> completableFuture = kafkaTemplate.send(topic, key, message);
        completableFuture.whenComplete((result, ex) -> {
            if (Objects.isNull(ex)) {
                if (successCallback != null) {
                    successCallback.onSuccess(result);
                }
                return;
            }
            if (failureCallback != null) {
                failureCallback.onFailure(ex);
            } else {
                log.error("send kafka message failed, topic:{}, key:{}, message:{}", topic, key, message, ex);
            }
        });
    }
}
