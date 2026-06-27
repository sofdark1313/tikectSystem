package com.tikectsystem.service.kafka;

import com.alibaba.fastjson.JSON;
import com.tikectsystem.domain.OrderCreateMq;
import com.tikectsystem.service.OrderService;
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
            if (orderCreateMq == null || orderCreateMq.getCreateOrderTime() == null ||
                    orderCreateMq.getOrderNumber() == null || orderCreateMq.getProgramId() == null ||
                    orderCreateMq.getUserId() == null || orderCreateMq.getOrderTicketUserCreateDtoList() == null ||
                    orderCreateMq.getOrderTicketUserCreateDtoList().isEmpty()) {
                log.error("create order kafka message format error, value : {}",value);
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
}
