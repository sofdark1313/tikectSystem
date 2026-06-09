package com.tikectsystem.service.delaysend;

import com.alibaba.fastjson2.JSON;
import com.baidu.fsg.uid.UidGenerator;
import com.tikectsystem.client.ApiDataClient;
import com.tikectsystem.common.ApiResponse;
import com.tikectsystem.core.SpringUtil;
import com.tikectsystem.dto.DelayOrderCancelDto;
import com.tikectsystem.dto.InsertMessageProducerRecordDto;
import com.tikectsystem.dto.UpdateMessageProducerRecordDto;
import com.tikectsystem.enums.BaseCode;
import com.tikectsystem.enums.MessageSendStatus;
import com.tikectsystem.enums.MessageType;
import com.tikectsystem.module.DelayOrderCancelMessageModule;
import com.tikectsystem.vo.MessageProducerRecordVo;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import static com.tikectsystem.constant.ProgramOrderConstant.DELAY_ORDER_CANCEL_TIME;
import static com.tikectsystem.constant.ProgramOrderConstant.DELAY_ORDER_CANCEL_TIME_UNIT;
import static com.tikectsystem.constant.ProgramOrderConstant.DELAY_ORDER_CANCEL_TOPIC;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 延迟订单发送
 * @author: 阿星不是程序员
 **/
@Slf4j
@Component
public class DelayOrderCancelSend {

    @Autowired
    private UidGenerator uidGenerator;
    
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    
    @Autowired
    private ApiDataClient apiDataClient;

    @Autowired
    private DelayOrderCancelSendExecutor delayOrderCancelSendExecutor;
    
    @Value("${delay.order.cancel:false}")
    private Boolean delayOrderCancel;

    private String topicName;

    @PostConstruct
    public void init() {
        topicName = SpringUtil.getPrefixDistinctionName() + "-" + DELAY_ORDER_CANCEL_TOPIC;
    }

    public void sendMessage(DelayOrderCancelDto delayOrderCancelDto){
        if (!Boolean.TRUE.equals(delayOrderCancel)){
            return;
        }
        if (delayOrderCancelDto == null || delayOrderCancelDto.getProgramId() == null ||
                delayOrderCancelDto.getOrderNumber() == null) {
            log.error("延迟订单取消消息参数为空 delayOrderCancelDto : {}", JSON.toJSONString(delayOrderCancelDto));
            return;
        }
        try {
            delayOrderCancelSendExecutor.execute(() -> doSendMessage(delayOrderCancelDto));
        } catch (Exception e) {
            log.error("延迟订单取消消息异步任务提交失败，降级为同步发送 delayOrderCancelDto : {}",
                    JSON.toJSONString(delayOrderCancelDto), e);
            doSendMessage(delayOrderCancelDto);
        }
    }

    private void doSendMessage(DelayOrderCancelDto delayOrderCancelDto) {
        try {
            Long messageTraceId = uidGenerator.getUid();
            Long messageId = uidGenerator.getUid();

            DelayOrderCancelMessageModule delayOrderCancelMessageModule = new DelayOrderCancelMessageModule();
            delayOrderCancelMessageModule.setMessageTraceId(messageTraceId);
            delayOrderCancelMessageModule.setMessageId(messageId);
            delayOrderCancelMessageModule.setProgramId(delayOrderCancelDto.getProgramId());
            delayOrderCancelMessageModule.setOrderNumber(delayOrderCancelDto.getOrderNumber());
            delayOrderCancelMessageModule.setExecuteTimestamp(System.currentTimeMillis() +
                    DELAY_ORDER_CANCEL_TIME_UNIT.toMillis(DELAY_ORDER_CANCEL_TIME));

            String messageContent = JSON.toJSONString(delayOrderCancelMessageModule);
            InsertMessageProducerRecordDto insertMessageProducerRecordDto = new InsertMessageProducerRecordDto();
            insertMessageProducerRecordDto.setMessageType(MessageType.DELAY_ORDER_CANCEL.getCode());
            insertMessageProducerRecordDto.setMessageTraceId(messageTraceId);
            insertMessageProducerRecordDto.setMessageBusinessesId(delayOrderCancelMessageModule.getProgramId());
            insertMessageProducerRecordDto.setMessageId(messageId);
            insertMessageProducerRecordDto.setMessageTopic(topicName);
            insertMessageProducerRecordDto.setMessageContent(messageContent);
            ApiResponse<MessageProducerRecordVo> insertMessageProducerRecordApiResponse = apiDataClient.insertMessageProducerRecord(insertMessageProducerRecordDto);
            if (insertMessageProducerRecordApiResponse == null ||
                    !BaseCode.SUCCESS.getCode().equals(insertMessageProducerRecordApiResponse.getCode())){
                log.error("添加记录消息发送日志失败，参数 : {}", JSON.toJSONString(insertMessageProducerRecordDto));
                return;
            }
            MessageProducerRecordVo messageProducerRecordVo = insertMessageProducerRecordApiResponse.getData();
            if (messageProducerRecordVo == null || messageProducerRecordVo.getId() == null) {
                log.error("添加记录消息发送日志返回数据为空，参数 : {}", JSON.toJSONString(insertMessageProducerRecordDto));
                return;
            }

            UpdateMessageProducerRecordDto updateMessageProducerRecordDto = new UpdateMessageProducerRecordDto();
            updateMessageProducerRecordDto.setId(messageProducerRecordVo.getId());

            try {
                log.debug("延迟订单取消消息发送到Kafka topic : {}, 消息体 : {}",topicName,messageContent);
                kafkaTemplate.send(topicName, String.valueOf(delayOrderCancelMessageModule.getOrderNumber()), messageContent)
                        .whenComplete((sendResult, ex) -> {
                            if (ex == null) {
                                updateMessageProducerRecordDto.setMessageSendStatus(MessageSendStatus.SEND_SUCCESS.getCode());
                            }else {
                                log.error("send delay order cancel kafka message error message : {}",messageContent,ex);
                                updateMessageProducerRecordDto.setMessageSendStatus(MessageSendStatus.SEND_FAIL.getCode());
                                updateMessageProducerRecordDto.setMessageSendException(ex.getMessage());
                            }
                            updateMessageProducerRecord(updateMessageProducerRecordDto, messageContent);
                        });
            }catch (Exception e) {
                log.error("send delay order cancel kafka message error message : {}",messageContent,e);
                updateMessageProducerRecordDto.setMessageSendStatus(MessageSendStatus.SEND_FAIL.getCode());
                updateMessageProducerRecordDto.setMessageSendException(e.getMessage());
                updateMessageProducerRecord(updateMessageProducerRecordDto, messageContent);
            }
        }catch (Exception e) {
            log.error("延迟订单取消消息异步发送任务执行失败 delayOrderCancelDto : {}", JSON.toJSONString(delayOrderCancelDto),e);
        }
    }

    private void updateMessageProducerRecord(UpdateMessageProducerRecordDto updateMessageProducerRecordDto, String messageContent) {
        try {
            apiDataClient.updateMessageProducerRecord(updateMessageProducerRecordDto);
        } catch (Exception e) {
            log.error("更新延迟订单取消消息发送记录失败 message : {}", messageContent, e);
        }
    }
}
