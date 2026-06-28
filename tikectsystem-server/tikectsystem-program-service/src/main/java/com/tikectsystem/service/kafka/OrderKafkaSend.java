package com.tikectsystem.service.kafka;

import com.baidu.fsg.uid.UidGenerator;
import com.tikectsystem.client.ApiDataClient;
import com.tikectsystem.common.ApiResponse;
import com.tikectsystem.core.SpringUtil;
import com.tikectsystem.dto.InsertMessageProducerRecordDto;
import com.tikectsystem.dto.UpdateMessageProducerRecordDto;
import com.tikectsystem.enums.BaseCode;
import com.tikectsystem.enums.MessageSendStatus;
import com.tikectsystem.enums.MessageType;
import com.tikectsystem.exception.TikectsystemFrameException;
import com.tikectsystem.mq.callback.FailureCallback;
import com.tikectsystem.mq.callback.SuccessCallback;
import com.tikectsystem.vo.MessageProducerRecordVo;
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

    @Autowired
    private ApiDataClient apiDataClient;

    @Autowired
    private UidGenerator uidGenerator;

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
        send(orderRequestTopicName, key, message, MessageType.ORDER_REQUEST, successCallback, failureCallback);
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
        send(orderCreateTopicName, key, message, MessageType.ORDER_CREATE, successCallback, failureCallback);
    }

    private void send(String topic, String key, String message, MessageType messageType,
                      SuccessCallback<SendResult<String, String>> successCallback, FailureCallback failureCallback) {
        Long producerRecordId = saveProducerRecord(topic, key, message, messageType);
        CompletableFuture<SendResult<String, String>> completableFuture = kafkaTemplate.send(topic, key, message);
        completableFuture.whenComplete((result, ex) -> {
            if (Objects.isNull(ex)) {
                updateProducerRecord(producerRecordId, MessageSendStatus.SEND_SUCCESS, null, message);
                if (successCallback != null) {
                    successCallback.onSuccess(result);
                }
                return;
            }
            updateProducerRecord(producerRecordId, MessageSendStatus.SEND_FAIL, ex.getMessage(), message);
            if (failureCallback != null) {
                failureCallback.onFailure(ex);
            } else {
                log.error("send kafka message failed, topic:{}, key:{}, message:{}", topic, key, message, ex);
            }
        });
    }

    private Long saveProducerRecord(String topic, String key, String message, MessageType messageType) {
        try {
            InsertMessageProducerRecordDto insertDto = new InsertMessageProducerRecordDto();
            Long businessId = parseMessageBusinessId(key);
            Long messageId = buildMessageId(businessId, messageType);
            insertDto.setMessageType(messageType.getCode());
            insertDto.setMessageTraceId(uidGenerator.getUid());
            insertDto.setMessageBusinessesId(businessId);
            insertDto.setMessageId(messageId);
            insertDto.setMessageKey(key);
            insertDto.setMessageTopic(topic);
            insertDto.setMessageContent(message);
            ApiResponse<MessageProducerRecordVo> response = apiDataClient.insertMessageProducerRecord(insertDto);
            if (response == null || !Objects.equals(response.getCode(), BaseCode.SUCCESS.getCode()) ||
                    response.getData() == null) {
                log.error("save kafka producer record failed, type:{}, topic:{}, key:{}", messageType, topic, key);
                throw new TikectsystemFrameException(BaseCode.SYSTEM_ERROR);
            }
            return response.getData().getId();
        } catch (Exception e) {
            log.error("save kafka producer record error, type:{}, topic:{}, key:{}", messageType, topic, key, e);
            throw new TikectsystemFrameException(e);
        }
    }

    private void updateProducerRecord(Long producerRecordId, MessageSendStatus sendStatus,
                                      String sendException, String message) {
        if (producerRecordId == null) {
            return;
        }
        try {
            UpdateMessageProducerRecordDto updateDto = new UpdateMessageProducerRecordDto();
            updateDto.setId(producerRecordId);
            updateDto.setMessageSendStatus(sendStatus.getCode());
            updateDto.setMessageSendException(sendException);
            ApiResponse<Boolean> response = apiDataClient.updateMessageProducerRecord(updateDto);
            if (response == null || !Objects.equals(response.getCode(), BaseCode.SUCCESS.getCode())) {
                log.error("update kafka producer record failed, message:{}", message);
            }
        } catch (Exception e) {
            log.error("update kafka producer record error, message:{}", message, e);
        }
    }

    private Long parseMessageBusinessId(String key) {
        try {
            return Long.valueOf(key);
        } catch (Exception e) {
            return uidGenerator.getUid();
        }
    }

    private Long buildMessageId(Long businessId, MessageType messageType) {
        if (Objects.equals(messageType, MessageType.ORDER_CREATE)) {
            return -businessId;
        }
        return businessId;
    }
}
