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

import java.util.Date;
import java.util.List;

@Slf4j
public abstract class AbstractKafkaExceptionMessageHandler implements ExceptionMessageHandler {

    @Autowired
    private MessageProducerRecordMapper messageProducerRecordMapper;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Override
    public List<MessageProducerRecord> noReconciliationMessageProducerRecordList() {
        Date beforeTime = DateUtils.addMinute(DateUtils.now(), -1);
        Wrapper<MessageProducerRecord> wrapper = Wrappers.lambdaQuery(MessageProducerRecord.class)
                .eq(MessageProducerRecord::getMessageType, getMessageType().getCode())
                .eq(MessageProducerRecord::getReconciliationStatus, ReconciliationStatus.RECONCILIATION_NO.getCode())
                .lt(MessageProducerRecord::getSendTime, beforeTime);
        return messageProducerRecordMapper.selectList(wrapper);
    }

    @Override
    public Boolean handle(MessageProducerRecord messageProducerRecord) {
        MessageProducerRecord updateRecord = new MessageProducerRecord();
        updateRecord.setId(messageProducerRecord.getId());
        updateRecord.setReconciliationStatus(ReconciliationStatus.RECONCILIATION_NO.getCode());
        updateRecord.setSendTime(DateUtils.now());
        try {
            kafkaTemplate.send(messageProducerRecord.getMessageTopic(),
                    buildKafkaKey(messageProducerRecord),
                    messageProducerRecord.getMessageContent()).whenComplete((sendResult, ex) -> {
                if (ex == null) {
                    updateRecord.setMessageSendStatus(MessageSendStatus.SEND_SUCCESS.getCode());
                } else {
                    log.error("resend kafka message failed, type:{}, message:{}",
                            getMessageType(), messageProducerRecord.getMessageContent(), ex);
                    updateRecord.setMessageSendStatus(MessageSendStatus.SEND_FAIL.getCode());
                    updateRecord.setMessageSendException(ex.getMessage());
                }
                updateMessageProducerRecord(updateRecord, messageProducerRecord.getMessageContent());
            });
        } catch (Exception e) {
            log.error("submit kafka resend failed, type:{}, message:{}",
                    getMessageType(), messageProducerRecord.getMessageContent(), e);
            updateRecord.setMessageSendStatus(MessageSendStatus.SEND_FAIL.getCode());
            updateRecord.setMessageSendException(e.getMessage());
            updateMessageProducerRecord(updateRecord, messageProducerRecord.getMessageContent());
        }
        return true;
    }

    private void updateMessageProducerRecord(MessageProducerRecord updateRecord, String messageContent) {
        try {
            messageProducerRecordMapper.updateById(updateRecord);
        } catch (Exception e) {
            log.error("update kafka producer record failed, message:{}", messageContent, e);
        }
    }

    private String buildKafkaKey(MessageProducerRecord messageProducerRecord) {
        if (messageProducerRecord.getMessageKey() != null && !messageProducerRecord.getMessageKey().isEmpty()) {
            return messageProducerRecord.getMessageKey();
        }
        return String.valueOf(messageProducerRecord.getMessageId());
    }

    @Override
    public abstract MessageType getMessageType();
}
