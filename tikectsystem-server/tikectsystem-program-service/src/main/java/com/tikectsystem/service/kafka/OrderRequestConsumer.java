package com.tikectsystem.service.kafka;

import com.alibaba.fastjson.JSON;
import com.tikectsystem.domain.OrderCreateMq;
import com.tikectsystem.enums.BaseCode;
import com.tikectsystem.exception.TikectsystemFrameException;
import com.tikectsystem.service.ProgramOrderService;
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
        OrderCreateMq orderCreateMq;
        try {
            orderCreateMq = programOrderService.reserveOrderRequest(orderRequestMq);
            acknowledge(acknowledgment);
        } catch (TikectsystemFrameException e) {
            if (isCircuitOpen(e)) {
                throw e;
            }
            acknowledge(acknowledgment);
            return;
        } catch (Exception e) {
            log.error("order_request infrastructure error, orderNumber:{}", orderRequestMq.getOrderNumber(), e);
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

    private boolean isCircuitOpen(TikectsystemFrameException exception) {
        return Objects.equals(exception.getCode(), BaseCode.PROGRAM_ORDER_CIRCUIT_OPEN.getCode()) ||
                Objects.equals(exception.getCode(), BaseCode.PROGRAM_ORDER_DEGRADE.getCode());
    }
}
