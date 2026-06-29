package com.tikectsystem.service.delaysend;

import com.alibaba.fastjson2.JSON;
import com.baidu.fsg.uid.UidGenerator;
import com.tikectsystem.core.SpringUtil;
import com.tikectsystem.dto.DelayOrderCancelDto;
import com.tikectsystem.module.DelayOrderCancelMessageModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import static com.tikectsystem.constant.ProgramOrderConstant.DELAY_ORDER_CANCEL_TIME;
import static com.tikectsystem.constant.ProgramOrderConstant.DELAY_ORDER_CANCEL_TIME_UNIT;
import static com.tikectsystem.service.constant.OrderConstant.DELAY_ORDER_CANCEL_TOPIC;

/**
 * 订单服务延迟取消消息发送器。
 */
@Slf4j
@Component
public class OrderDelayOrderCancelSend {

    @Autowired
    private UidGenerator uidGenerator;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Value("${delay.order.cancel:true}")
    private Boolean delayOrderCancel;

    /**
     * 订单创建成功后发送延迟取消消息。
     * @param delayOrderCancelDto 取消消息参数
     */
    public void sendMessage(DelayOrderCancelDto delayOrderCancelDto) {
        if (!Boolean.TRUE.equals(delayOrderCancel)) {
            return;
        }
        if (delayOrderCancelDto == null || delayOrderCancelDto.getProgramId() == null ||
                delayOrderCancelDto.getOrderNumber() == null) {
            log.error("delay order cancel param is empty, param:{}", JSON.toJSONString(delayOrderCancelDto));
            return;
        }
        Long messageTraceId = uidGenerator.getUid();
        Long messageId = uidGenerator.getUid();
        DelayOrderCancelMessageModule messageModule = new DelayOrderCancelMessageModule();
        messageModule.setMessageTraceId(messageTraceId);
        messageModule.setMessageId(messageId);
        messageModule.setProgramId(delayOrderCancelDto.getProgramId());
        messageModule.setOrderNumber(delayOrderCancelDto.getOrderNumber());
        messageModule.setExecuteTimestamp(System.currentTimeMillis() +
                DELAY_ORDER_CANCEL_TIME_UNIT.toMillis(DELAY_ORDER_CANCEL_TIME));

        String topicName = SpringUtil.getPrefixDistinctionName() + "-" + DELAY_ORDER_CANCEL_TOPIC;
        String messageContent = JSON.toJSONString(messageModule);

        try {
            kafkaTemplate.send(topicName, String.valueOf(messageModule.getOrderNumber()), messageContent)
                    .whenComplete((sendResult, ex) -> {
                        if (ex != null) {
                            log.error("send delay order cancel kafka message error, message:{}", messageContent, ex);
                        }
                    });
        } catch (Exception e) {
            log.error("send delay order cancel kafka message error, message:{}", messageContent, e);
        }
    }
}
