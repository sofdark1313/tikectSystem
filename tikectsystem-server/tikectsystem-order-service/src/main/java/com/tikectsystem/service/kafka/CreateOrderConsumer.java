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
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.tikectsystem.constant.Constant.SPRING_INJECT_PREFIX_DISTINCTION_NAME;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: kafka 创建订单 消费
 * @author: 阿星不是程序员
 **/
@Slf4j
@AllArgsConstructor
@Component
public class CreateOrderConsumer {
    
    @Autowired
    private OrderService orderService;
    
    @Autowired
    private RedisCache redisCache;
    
    public static Long MESSAGE_DELAY_TIME = 5000L;
    
    @KafkaListener(topics = {SPRING_INJECT_PREFIX_DISTINCTION_NAME+"-"+"${spring.kafka.topic:create_order}"})
    public void consumerOrderMessage(ConsumerRecord<String,String> consumerRecord){
        try {
            Optional.ofNullable(consumerRecord.value()).map(String::valueOf).ifPresent(value -> {
      
                OrderCreateMq orderCreateMq = JSON.parseObject(value, OrderCreateMq.class);
                if (orderCreateMq == null || orderCreateMq.getCreateOrderTime() == null ||
                        orderCreateMq.getOrderNumber() == null) {
                    log.error("消费到kafka的创建订单消息格式错误 消息体: {}",value);
                    return;
                }
                
                long createOrderTimeTimestamp = orderCreateMq.getCreateOrderTime().getTime();
                
                long currentTimeTimestamp = System.currentTimeMillis();
                
                long delayTime = currentTimeTimestamp - createOrderTimeTimestamp;
                
                log.info("消费到kafka的创建订单消息 消息体: {} 延迟时间 : {} 毫秒",value,delayTime);
                
       
                if (currentTimeTimestamp - createOrderTimeTimestamp > MESSAGE_DELAY_TIME) {
                    List<OrderTicketUserCreateDto> orderTicketUserCreateDtoList =
                            Optional.ofNullable(orderCreateMq.getOrderTicketUserCreateDtoList()).orElse(Collections.emptyList());
                    Map<Long, List<OrderTicketUserCreateDto>> orderTicketUserSeatList =
                            orderTicketUserCreateDtoList.stream().collect(Collectors.groupingBy(OrderTicketUserCreateDto::getTicketCategoryId));
                    //key: 节目票档id value: 座位id集合
                    Map<Long,List<Long>> seatMap = new HashMap<>(orderTicketUserSeatList.size());
                    orderTicketUserSeatList.forEach((k,v) -> {
                        seatMap.put(k,v.stream().map(OrderTicketUserCreateDto::getSeatId).collect(Collectors.toList()));
                    });
                    log.info("消费到kafka的创建订单消息延迟时间大于了 {} 毫秒 此订单消息被丢弃 订单号 : {} 座位信息 : {}",
                            delayTime,orderCreateMq.getOrderNumber(),JSON.toJSONString(seatMap));
                    //将延迟丢弃的订单放入redis中
                    redisCache.leftPushForList(RedisKeyBuild.createRedisKey(RedisKeyManage.DISCARD_ORDER,
                            orderCreateMq.getProgramId()),new DiscardOrder(orderCreateMq, DiscardOrderReason.CONSUMER_DELAY.getCode()));
                }else {
                    String orderNumber = orderService.createMq(orderCreateMq);
                    log.info("消费到kafka的创建订单消息 创建订单成功 订单号 : {}",orderNumber);
                }
            });
        }catch (Exception e) {
            log.error("处理消费到kafka的创建订单消息失败 error",e);
        }
    }
}
