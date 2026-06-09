package com.tikectsystem.service.kafka;

import com.tikectsystem.core.SpringUtil;
import com.tikectsystem.mq.callback.FailureCallback;
import com.tikectsystem.mq.callback.SuccessCallback;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: kafka 创建订单 发送
 * @author: 阿星不是程序员
 **/
@Slf4j
@AllArgsConstructor
@Component
public class CreateOrderSend {
    
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    
    @Autowired
    private KafkaTopic kafkaTopic;
    
    
    public void sendMessage(String message, SuccessCallback<SendResult<String, String>> successCallback, 
                            FailureCallback failureCallback) {
        log.info("创建订单kafka发送消息 消息体 : {}", message);
        CompletableFuture<SendResult<String, String>> completableFuture = 
                kafkaTemplate.send(SpringUtil.getPrefixDistinctionName() + "-" + kafkaTopic.getTopic(), message);
        completableFuture.whenComplete((result,ex) -> {
            if (Objects.isNull(ex)) {
                if (successCallback != null) {
                    successCallback.onSuccess(result);
                }
            }else {
                if (failureCallback != null) {
                    failureCallback.onFailure(ex);
                } else {
                    log.error("创建订单kafka发送消息失败 消息体 : {}",message,ex);
                }
            }
        });
    }
}
