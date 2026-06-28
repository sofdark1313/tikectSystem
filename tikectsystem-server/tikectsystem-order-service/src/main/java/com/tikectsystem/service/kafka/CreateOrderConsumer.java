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
import com.tikectsystem.service.OrderService;
import com.tikectsystem.util.DateUtils;
import com.tikectsystem.vo.MessageConsumerRecordVo;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import static com.tikectsystem.constant.Constant.SPRING_INJECT_PREFIX_DISTINCTION_NAME;

@Slf4j
@Component
public class CreateOrderConsumer {
    
    @Autowired
    private OrderService orderService;

    @Autowired
    private ApiDataClient apiDataClient;
    
    @KafkaListener(topics = {SPRING_INJECT_PREFIX_DISTINCTION_NAME+"-"+"${spring.kafka.topic:create_order}"})
    public void consumerOrderMessage(ConsumerRecord<String,String> consumerRecord){
        if (consumerRecord == null || consumerRecord.value() == null) {
            return;
        }
        String value = consumerRecord.value();
        MessageConsumerRecordVo consumerRecordVo = null;
        try {
            OrderCreateMq orderCreateMq;
            try {
                orderCreateMq = JSON.parseObject(value, OrderCreateMq.class);
            } catch (Exception e) {
                log.error("create order kafka message parse error, value : {}", value, e);
                return;
            }
            if (orderCreateMq == null || orderCreateMq.getCreateOrderTime() == null ||
                    orderCreateMq.getOrderNumber() == null || orderCreateMq.getProgramId() == null ||
                    orderCreateMq.getUserId() == null || orderCreateMq.getOrderTicketUserCreateDtoList() == null ||
                    orderCreateMq.getOrderTicketUserCreateDtoList().isEmpty()) {
                log.error("create order kafka message format error, value : {}",value);
                return;
            }

            long delayTime = System.currentTimeMillis() - orderCreateMq.getCreateOrderTime().getTime();
            log.debug("consume create order kafka message, value : {}, delay : {} ms",value,delayTime);
            consumerRecordVo = prepareConsumerRecord(consumerRecord, orderCreateMq.getOrderNumber());
            if (consumerRecordVo != null && MessageConsumerStatus.CONSUMER_SUCCESS.getCode()
                    .equals(consumerRecordVo.getMessageConsumerStatus())) {
                return;
            }
            String orderNumber = orderService.createMq(orderCreateMq);
            updateConsumerRecord(consumerRecordVo, MessageConsumerStatus.CONSUMER_SUCCESS, null);
            log.debug("consume create order kafka message success, orderNumber : {}",orderNumber);
        }catch (Exception e) {
            log.error("handle create order kafka message error",e);
            updateConsumerRecord(consumerRecordVo, MessageConsumerStatus.CONSUMER_FAIL, e.getMessage());
            throw new IllegalStateException(e);
        }
    }

    private MessageConsumerRecordVo prepareConsumerRecord(ConsumerRecord<String, String> consumerRecord, Long orderNumber) {
        Long messageId = -orderNumber;
        MessageIdDto messageIdDto = new MessageIdDto();
        messageIdDto.setMessageId(messageId);
        ApiResponse<MessageConsumerRecordVo> existResponse = apiDataClient.getMessageConsumerByMessageId(messageIdDto);
        if (existResponse != null && BaseCode.SUCCESS.getCode().equals(existResponse.getCode()) &&
                existResponse.getData() != null) {
            return existResponse.getData();
        }
        InsertMessageConsumerRecordDto insertDto = new InsertMessageConsumerRecordDto();
        insertDto.setMessageType(MessageType.ORDER_CREATE.getCode());
        insertDto.setMessageTraceId(messageId);
        insertDto.setMessageBusinessesId(orderNumber);
        insertDto.setMessageId(messageId);
        insertDto.setMessageTopic(consumerRecord.topic());
        insertDto.setMessageContent(consumerRecord.value());
        ApiResponse<MessageConsumerRecordVo> insertResponse = apiDataClient.insertMessageConsumerRecord(insertDto);
        if (insertResponse == null || !BaseCode.SUCCESS.getCode().equals(insertResponse.getCode())) {
            log.error("insert order_create consumer record failed, orderNumber:{}", orderNumber);
            return null;
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
}
