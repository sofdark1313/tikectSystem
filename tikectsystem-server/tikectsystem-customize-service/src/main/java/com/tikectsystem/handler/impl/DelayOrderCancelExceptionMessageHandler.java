package com.tikectsystem.handler.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tikectsystem.entity.MessageProducerRecord;
import com.tikectsystem.enums.MessageSendStatus;
import com.tikectsystem.enums.MessageType;
import com.tikectsystem.enums.ReconciliationStatus;
import com.tikectsystem.handler.ExceptionMessageHandler;
import com.tikectsystem.mapper.MessageProducerRecordMapper;
import com.tikectsystem.util.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

import static com.tikectsystem.constant.ProgramOrderConstant.DELAY_ORDER_CANCEL_TIME;
import static com.tikectsystem.constant.ProgramOrderConstant.DELAY_ORDER_CANCEL_TIME_UNIT;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 延迟订单取消异常消息处理
 * @author: 阿星不是程序员
 **/
@Slf4j
@Component
public class DelayOrderCancelExceptionMessageHandler implements ExceptionMessageHandler {
    
    @Autowired
    private MessageProducerRecordMapper messageProducerRecordMapper;
    
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    
    @Override
    public List<MessageProducerRecord> noReconciliationMessageProducerRecordList() {
        Date date;
        switch (DELAY_ORDER_CANCEL_TIME_UNIT) {
            case MINUTES -> date = DateUtils.addMinute(DateUtils.now(),-DELAY_ORDER_CANCEL_TIME.intValue());
            case HOURS -> date = DateUtils.addHour(DateUtils.now(),-DELAY_ORDER_CANCEL_TIME.intValue());
            case DAYS -> date = DateUtils.addDay(DateUtils.now(),-DELAY_ORDER_CANCEL_TIME.intValue());
            default -> date = DateUtils.addSecond(DateUtils.now(),-DELAY_ORDER_CANCEL_TIME.intValue());
        }
        //查询出所有未对账成功，并且发送时间小于 当前时间-延迟时间-10秒 的消息记录
        Wrapper<MessageProducerRecord> messageRecordWrapper = Wrappers.lambdaQuery(MessageProducerRecord.class)
                .eq(MessageProducerRecord::getMessageType, MessageType.DELAY_ORDER_CANCEL.getCode())
                .eq(MessageProducerRecord::getReconciliationStatus, ReconciliationStatus.RECONCILIATION_NO.getCode())
                .lt(MessageProducerRecord::getSendTime, DateUtils.addSecond(date, -10));
        return messageProducerRecordMapper.selectList(messageRecordWrapper);
    }
    
    @Override
    public Boolean handle(MessageProducerRecord messageProducerRecord) {
        String messageContent = messageProducerRecord.getMessageContent();
        MessageProducerRecord updateMessageProducerRecord = new MessageProducerRecord();
        updateMessageProducerRecord.setId(messageProducerRecord.getId());
        //因为是处理异常消息，所以这里直接设置为未对账
        updateMessageProducerRecord.setReconciliationStatus(ReconciliationStatus.RECONCILIATION_NO.getCode());
        updateMessageProducerRecord.setSendTime(DateUtils.now());
        try {
            log.info("延迟订单取消异常消息发送到Kafka topic : {}, 消息体 : {}",
                    messageProducerRecord.getMessageTopic(), messageContent);
            kafkaTemplate.send(messageProducerRecord.getMessageTopic(),
                    buildKafkaKey(messageProducerRecord), messageContent).whenComplete((sendResult, ex) -> {
                if (ex == null) {
                    updateMessageProducerRecord.setMessageSendStatus(MessageSendStatus.SEND_SUCCESS.getCode());
                }else {
                    log.error("send delay order cancel kafka message error message : {}",messageContent,ex);
                    updateMessageProducerRecord.setMessageSendStatus(MessageSendStatus.SEND_FAIL.getCode());
                    updateMessageProducerRecord.setMessageSendException(ex.getMessage());
                }
                updateMessageProducerRecord(updateMessageProducerRecord, messageContent);
            });
        }catch (Exception e) {
            log.error("send delay order cancel kafka message error message : {}",messageContent,e);
            //发送失败，更新发送失败状态
            updateMessageProducerRecord.setMessageSendStatus(MessageSendStatus.SEND_FAIL.getCode());
            updateMessageProducerRecord.setMessageSendException(e.getMessage());
            updateMessageProducerRecord(updateMessageProducerRecord, messageContent);
        }
        return true;
    }

    private void updateMessageProducerRecord(MessageProducerRecord updateMessageProducerRecord, String messageContent) {
        try {
            messageProducerRecordMapper.updateById(updateMessageProducerRecord);
        } catch (Exception e) {
            log.error("更新延迟订单取消消息发送记录失败 message : {}", messageContent, e);
        }
    }

    private String buildKafkaKey(MessageProducerRecord messageProducerRecord) {
        if (messageProducerRecord.getMessageKey() != null && !messageProducerRecord.getMessageKey().isEmpty()) {
            return messageProducerRecord.getMessageKey();
        }
        return String.valueOf(messageProducerRecord.getMessageId());
    }
    
    @Override
    public MessageType getMessageType() {
        return MessageType.DELAY_ORDER_CANCEL;
    }
}
