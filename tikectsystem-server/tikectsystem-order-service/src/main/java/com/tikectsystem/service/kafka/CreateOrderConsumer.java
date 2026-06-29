package com.tikectsystem.service.kafka;

import com.alibaba.fastjson.JSON;
import com.tikectsystem.domain.OrderCreateMq;
import com.tikectsystem.dto.OrderTicketUserCreateDto;
import com.tikectsystem.enums.BaseCode;
import com.tikectsystem.service.OrderService;
import com.tikectsystem.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

import static com.tikectsystem.constant.Constant.SPRING_INJECT_PREFIX_DISTINCTION_NAME;

/**
 * 创建订单消息消费端。
 */
@Slf4j
@Component
public class CreateOrderConsumer {
    
    @Autowired
    private OrderService orderService;

    /**
     * 消费 create_order 消息并创建订单。
     *
     * @param consumerRecord Kafka 消息
     */
    @KafkaListener(topics = {SPRING_INJECT_PREFIX_DISTINCTION_NAME+"-"+"${spring.kafka.topic:create_order}"})
    public void consumerOrderMessage(ConsumerRecord<String,String> consumerRecord){
        if (consumerRecord == null || consumerRecord.value() == null) {
            return;
        }
        String value = consumerRecord.value();
        try {
            OrderCreateMq orderCreateMq;
            try {
                orderCreateMq = JSON.parseObject(value, OrderCreateMq.class);
            } catch (Exception e) {
                log.error("create order kafka message parse error, value : {}", value, e);
                return;
            }
            if (!isValidOrderCreateMq(orderCreateMq)) {
                log.error("create order kafka message format error, value : {}",value);
                orderService.markCreateOrderRequestFailedSafely(orderCreateMq, BaseCode.PARAMETER_ERROR);
                return;
            }

            long delayTime = System.currentTimeMillis() - orderCreateMq.getCreateOrderTime().getTime();
            log.debug("consume create order kafka message, value : {}, delay : {} ms",value,delayTime);
            String orderNumber = orderService.createMq(orderCreateMq);
            log.debug("consume create order kafka message success, orderNumber : {}",orderNumber);
        }catch (Exception e) {
            log.error("handle create order kafka message error",e);
            throw new IllegalStateException(e);
        }
    }

    private boolean isValidOrderCreateMq(OrderCreateMq orderCreateMq) {
        if (orderCreateMq == null || orderCreateMq.getCreateOrderTime() == null ||
                orderCreateMq.getOrderNumber() == null || orderCreateMq.getProgramId() == null ||
                orderCreateMq.getUserId() == null || orderCreateMq.getOrderPrice() == null ||
                orderCreateMq.getOrderVersion() == null) {
            return false;
        }
        List<OrderTicketUserCreateDto> ticketUserList = orderCreateMq.getOrderTicketUserCreateDtoList();
        if (ticketUserList == null || ticketUserList.isEmpty()) {
            return false;
        }
        for (OrderTicketUserCreateDto ticketUser : ticketUserList) {
            if (ticketUser == null || ticketUser.getOrderNumber() == null ||
                    ticketUser.getProgramId() == null || ticketUser.getUserId() == null ||
                    ticketUser.getTicketUserId() == null || ticketUser.getSeatId() == null ||
                    ticketUser.getTicketCategoryId() == null || ticketUser.getOrderPrice() == null ||
                    ticketUser.getCreateOrderTime() == null || StringUtil.isEmpty(ticketUser.getSeatInfo())) {
                return false;
            }
            if (!Objects.equals(ticketUser.getOrderNumber(), orderCreateMq.getOrderNumber()) ||
                    !Objects.equals(ticketUser.getProgramId(), orderCreateMq.getProgramId()) ||
                    !Objects.equals(ticketUser.getUserId(), orderCreateMq.getUserId())) {
                return false;
            }
        }
        return true;
    }
}
