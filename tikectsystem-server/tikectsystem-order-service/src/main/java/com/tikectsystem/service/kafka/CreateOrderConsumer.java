package com.tikectsystem.service.kafka;

import com.alibaba.fastjson.JSON;
import com.tikectsystem.core.RedisKeyManage;
import com.tikectsystem.domain.DiscardOrder;
import com.tikectsystem.domain.OrderCreateMq;
import com.tikectsystem.dto.OrderTicketUserCreateDto;
import com.tikectsystem.enums.DiscardOrderReason;
import com.tikectsystem.redis.RedisCache;
import com.tikectsystem.redis.RedisKeyBuild;
import com.tikectsystem.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.tikectsystem.constant.Constant.SPRING_INJECT_PREFIX_DISTINCTION_NAME;

@Slf4j
@Component
public class CreateOrderConsumer {
    
    @Autowired
    private OrderService orderService;
    
    @Autowired
    private RedisCache redisCache;
    
    public static Long MESSAGE_DELAY_TIME = 5000L;
    
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

            long createOrderTimeTimestamp = orderCreateMq.getCreateOrderTime().getTime();
            long currentTimeTimestamp = System.currentTimeMillis();
            long delayTime = currentTimeTimestamp - createOrderTimeTimestamp;
            log.debug("consume create order kafka message, value : {}, delay : {} ms",value,delayTime);

            if (delayTime > MESSAGE_DELAY_TIME) {
                List<OrderTicketUserCreateDto> orderTicketUserCreateDtoList =
                        orderCreateMq.getOrderTicketUserCreateDtoList();
                Map<Long,List<Long>> seatMap = new HashMap<>(orderTicketUserCreateDtoList.size());
                for (OrderTicketUserCreateDto orderTicketUserCreateDto : orderTicketUserCreateDtoList) {
                    seatMap.computeIfAbsent(orderTicketUserCreateDto.getTicketCategoryId(), key -> new ArrayList<>())
                            .add(orderTicketUserCreateDto.getSeatId());
                }
                log.info("create order kafka message delayed more than {} ms, discard orderNumber : {}, seatMap : {}",
                        delayTime,orderCreateMq.getOrderNumber(),JSON.toJSONString(seatMap));
                redisCache.leftPushForList(RedisKeyBuild.createRedisKey(RedisKeyManage.DISCARD_ORDER,
                        orderCreateMq.getProgramId()),new DiscardOrder(orderCreateMq, DiscardOrderReason.CONSUMER_DELAY.getCode()));
                return;
            }
            String orderNumber = orderService.createMq(orderCreateMq);
            log.debug("consume create order kafka message success, orderNumber : {}",orderNumber);
        }catch (Exception e) {
            log.error("handle create order kafka message error",e);
        }
    }
}
