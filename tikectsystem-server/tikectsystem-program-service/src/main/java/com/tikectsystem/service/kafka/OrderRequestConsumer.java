package com.tikectsystem.service.kafka;

import com.alibaba.fastjson.JSON;
import com.tikectsystem.client.ApiDataClient;
import com.tikectsystem.common.ApiResponse;
import com.tikectsystem.domain.OrderCreateMq;
import com.tikectsystem.dto.InsertMessageConsumerRecordDto;
import com.tikectsystem.dto.MessageIdDto;
import com.tikectsystem.dto.UpdateMessageConsumerRecordDto;
import com.tikectsystem.enums.BaseCode;
import com.tikectsystem.enums.MessageConsumerStatus;
import com.tikectsystem.enums.MessageType;
import com.tikectsystem.exception.TikectsystemFrameException;
import com.tikectsystem.service.OrderRequestResultService;
import com.tikectsystem.service.ProgramOrderService;
import com.tikectsystem.util.DateUtils;
import com.tikectsystem.vo.MessageConsumerRecordVo;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Objects;

import static com.tikectsystem.constant.Constant.SPRING_INJECT_PREFIX_DISTINCTION_NAME;

/**
 * 下单受理请求消费者。
 */
@Slf4j
@Component
public class OrderRequestConsumer {

    @Autowired
    private ProgramOrderService programOrderService;

    @Autowired
    private OrderRequestResultService orderRequestResultService;

    @Autowired
    private ApiDataClient apiDataClient;

    /**
     * 消费 order_request，完成最终锁座并投递 order_create。
     * @param consumerRecord Kafka 消息
     * @param acknowledgment 手动 offset 确认
     */
    @KafkaListener(topics = {SPRING_INJECT_PREFIX_DISTINCTION_NAME + "-" + "${spring.kafka.order-request-topic:order_request}"})
    public void consume(ConsumerRecord<String, String> consumerRecord, Acknowledgment acknowledgment) {
        if (consumerRecord == null || consumerRecord.value() == null) {
            acknowledge(acknowledgment);
            return;
        }
        String value = consumerRecord.value();
        OrderRequestMq orderRequestMq;
        try {
            orderRequestMq = JSON.parseObject(value, OrderRequestMq.class);
        } catch (Exception e) {
            log.error("order_request message parse error, value:{}", value, e);
            acknowledge(acknowledgment);
            return;
        }
        if (orderRequestMq == null || orderRequestMq.getOrderNumber() == null ||
                orderRequestMq.getProgramOrderCreateDto() == null) {
            log.error("order_request message format error, value:{}", value);
            acknowledge(acknowledgment);
            return;
        }
        MessageConsumerRecordVo consumerRecordVo = prepareConsumerRecord(consumerRecord, orderRequestMq.getOrderNumber(),
                MessageType.ORDER_REQUEST);
        if (consumerRecordVo != null && Objects.equals(consumerRecordVo.getMessageConsumerStatus(),
                MessageConsumerStatus.CONSUMER_SUCCESS.getCode())) {
            acknowledge(acknowledgment);
            return;
        }
        OrderCreateMq orderCreateMq;
        try {
            orderCreateMq = programOrderService.reserveOrderRequest(orderRequestMq);
            acknowledge(acknowledgment);
            updateConsumerRecordSafely(consumerRecordVo, MessageConsumerStatus.CONSUMER_SUCCESS, null);
        } catch (TikectsystemFrameException e) {
            orderRequestResultService.markFailed(orderRequestMq.getOrderNumber(), String.valueOf(e.getCode()), e.getMessage());
            updateConsumerRecord(consumerRecordVo, MessageConsumerStatus.CONSUMER_SUCCESS, null);
            acknowledge(acknowledgment);
            return;
        } catch (Exception e) {
            log.error("order_request infrastructure error, orderNumber:{}", orderRequestMq.getOrderNumber(), e);
            updateConsumerRecord(consumerRecordVo, MessageConsumerStatus.CONSUMER_FAIL, e.getMessage());
            throw e;
        }
        if (orderCreateMq == null) {
            return;
        }
        try {
            programOrderService.sendReservedOrderCreate(orderCreateMq);
        } catch (RuntimeException e) {
            log.error("order_create send failed after order_request offset committed, wait recovery scan, orderNumber:{}",
                    orderRequestMq.getOrderNumber(), e);
        }
    }

    private void acknowledge(Acknowledgment acknowledgment) {
        if (Objects.nonNull(acknowledgment)) {
            acknowledgment.acknowledge();
        }
    }

    private MessageConsumerRecordVo prepareConsumerRecord(ConsumerRecord<String, String> consumerRecord, Long orderNumber,
                                                          MessageType messageType) {
        Long messageId = buildMessageId(orderNumber, messageType);
        MessageIdDto messageIdDto = new MessageIdDto();
        messageIdDto.setMessageId(messageId);
        ApiResponse<MessageConsumerRecordVo> existResponse = apiDataClient.getMessageConsumerByMessageId(messageIdDto);
        if (existResponse != null && Objects.equals(existResponse.getCode(), BaseCode.SUCCESS.getCode()) &&
                existResponse.getData() != null) {
            return existResponse.getData();
        }
        if (existResponse != null && !Objects.equals(existResponse.getCode(), BaseCode.SUCCESS.getCode())) {
            throw new IllegalStateException("query order_request consumer record failed");
        }
        InsertMessageConsumerRecordDto insertDto = new InsertMessageConsumerRecordDto();
        insertDto.setMessageType(messageType.getCode());
        insertDto.setMessageTraceId(messageId);
        insertDto.setMessageBusinessesId(orderNumber);
        insertDto.setMessageId(messageId);
        insertDto.setMessageTopic(consumerRecord.topic());
        insertDto.setMessageContent(consumerRecord.value());
        ApiResponse<MessageConsumerRecordVo> insertResponse = apiDataClient.insertMessageConsumerRecord(insertDto);
        if (insertResponse == null || !Objects.equals(insertResponse.getCode(), BaseCode.SUCCESS.getCode()) ||
                insertResponse.getData() == null) {
            log.error("insert order_request consumer record failed, orderNumber:{}", orderNumber);
            throw new IllegalStateException("insert order_request consumer record failed");
        }
        return insertResponse.getData();
    }

    private void updateConsumerRecord(MessageConsumerRecordVo consumerRecordVo, MessageConsumerStatus status,
                                      String exceptionMessage) {
        if (consumerRecordVo == null || consumerRecordVo.getId() == null) {
            return;
        }
        UpdateMessageConsumerRecordDto updateDto = new UpdateMessageConsumerRecordDto();
        updateDto.setId(consumerRecordVo.getId());
        updateDto.setMessageConsumerStatus(status.getCode());
        updateDto.setMessageConsumerException(exceptionMessage);
        updateDto.setMessageConsumerCount(consumerRecordVo.getMessageConsumerCount() == null ? 1 :
                consumerRecordVo.getMessageConsumerCount() + 1);
        updateDto.setConsumerTime(DateUtils.now());
        apiDataClient.updateMessageConsumerRecord(updateDto);
    }

    private void updateConsumerRecordSafely(MessageConsumerRecordVo consumerRecordVo, MessageConsumerStatus status,
                                            String exceptionMessage) {
        try {
            updateConsumerRecord(consumerRecordVo, status, exceptionMessage);
        } catch (RuntimeException e) {
            log.warn("update order_request consumer record failed after offset committed", e);
        }
    }

    private Long buildMessageId(Long orderNumber, MessageType messageType) {
        if (Objects.equals(messageType, MessageType.ORDER_CREATE)) {
            return -orderNumber;
        }
        return orderNumber;
    }
}
