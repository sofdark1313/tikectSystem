package com.tikectsystem.service.delayconsumer;

import com.alibaba.fastjson.JSON;
import com.tikectsystem.client.ApiDataClient;
import com.tikectsystem.common.ApiResponse;
import com.tikectsystem.core.ConsumerTask;
import com.tikectsystem.core.SpringUtil;
import com.tikectsystem.dto.InsertMessageConsumerRecordDto;
import com.tikectsystem.dto.MessageIdDto;
import com.tikectsystem.dto.OrderCancelDto;
import com.tikectsystem.dto.UpdateMessageConsumerRecordDto;
import com.tikectsystem.enums.BaseCode;
import com.tikectsystem.enums.MessageConsumerStatus;
import com.tikectsystem.enums.MessageType;
import com.tikectsystem.exception.TikectsystemFrameException;
import com.tikectsystem.module.DelayOrderCancelMessageModule;
import com.tikectsystem.service.OrderService;
import com.tikectsystem.util.DateUtils;
import com.tikectsystem.util.StringUtil;
import com.tikectsystem.vo.MessageConsumerRecordVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;

import static com.tikectsystem.service.constant.OrderConstant.DELAY_ORDER_CANCEL_TOPIC;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 延迟订单取消
 * @author: 阿星不是程序员
 **/
@Slf4j
@Component
public class DelayOrderCancelConsumer implements ConsumerTask {
    
    @Autowired
    private OrderService orderService;
    
    @Autowired
    private ApiDataClient apiDataClient;
    
    
    @Override
    public void execute(String content) {
        log.info("延迟订单取消消息进行消费 content : {}", content);
        if (StringUtil.isEmpty(content)) {
            log.error("延迟队列消息不存在");
            return;
        }
        DelayOrderCancelMessageModule delayOrderCancelMessageModule;
        try {
            delayOrderCancelMessageModule = JSON.parseObject(content, DelayOrderCancelMessageModule.class);
        } catch (Exception e) {
            log.error("寤惰繜闃熷垪娑堟伅瑙ｆ瀽澶辫触 content : {}", content, e);
            return;
        }
        if (delayOrderCancelMessageModule == null) {
            log.error("延迟队列消息格式错误 content : {}", content);
            return;
        }
        
        Long messageTraceId = delayOrderCancelMessageModule.getMessageTraceId();
        Long messageId = delayOrderCancelMessageModule.getMessageId();
        Long programId = delayOrderCancelMessageModule.getProgramId();
        Long orderNumber = delayOrderCancelMessageModule.getOrderNumber();
        if (messageId == null || orderNumber == null) {
            log.error("延迟队列消息缺少必要参数 content : {}", content);
            return;
        }
        
        MessageIdDto messageIdDto = new MessageIdDto();
        messageIdDto.setMessageId(messageId);
        ApiResponse<MessageConsumerRecordVo> apiResponse = apiDataClient.getMessageConsumerByMessageId(messageIdDto);
        if (apiResponse == null || !Objects.equals(apiResponse.getCode(),BaseCode.SUCCESS.getCode())) {
            log.error("查询消息消费记录失败 messageId : {}",messageId);
            return;
        }
        
        MessageConsumerRecordVo existMessageConsumerRecordVo = apiResponse.getData();
        
        if (Objects.nonNull(existMessageConsumerRecordVo) &&
                Objects.equals(existMessageConsumerRecordVo.getMessageConsumerStatus(),MessageConsumerStatus.CONSUMER_SUCCESS.getCode())) {
            return;
        }
        Long messageConsumerRecordId = null;
        Integer messageConsumerCount;
        if (Objects.isNull(existMessageConsumerRecordVo)) {
            InsertMessageConsumerRecordDto insertMessageConsumerRecordDto = new InsertMessageConsumerRecordDto();
            insertMessageConsumerRecordDto.setMessageId(messageId);
            insertMessageConsumerRecordDto.setMessageTraceId(messageTraceId);
            insertMessageConsumerRecordDto.setMessageType(MessageType.DELAY_ORDER_CANCEL.getCode());
            insertMessageConsumerRecordDto.setMessageBusinessesId(programId);
            insertMessageConsumerRecordDto.setMessageTopic(SpringUtil.getPrefixDistinctionName() + "-" + DELAY_ORDER_CANCEL_TOPIC);
            insertMessageConsumerRecordDto.setMessageContent(content);
            ApiResponse<MessageConsumerRecordVo> insertApiResponse = apiDataClient.insertMessageConsumerRecord(insertMessageConsumerRecordDto);
            if (insertApiResponse == null || !Objects.equals(insertApiResponse.getCode(),BaseCode.SUCCESS.getCode())) {
                log.error("添加消息消费记录失败 insertMessageConsumerRecordDto : {}", JSON.toJSONString(insertMessageConsumerRecordDto));
                return;
            }
            MessageConsumerRecordVo saveMessageConsumerRecordVo = insertApiResponse.getData();
            if (saveMessageConsumerRecordVo == null || saveMessageConsumerRecordVo.getId() == null) {
                log.error("添加消息消费记录返回数据为空 insertMessageConsumerRecordDto : {}", JSON.toJSONString(insertMessageConsumerRecordDto));
                return;
            }
            messageConsumerRecordId = saveMessageConsumerRecordVo.getId();
            messageConsumerCount = saveMessageConsumerRecordVo.getMessageConsumerCount() == null ? 1 :
                    saveMessageConsumerRecordVo.getMessageConsumerCount();
        }else {
            messageConsumerRecordId = existMessageConsumerRecordVo.getId();
            messageConsumerCount = (existMessageConsumerRecordVo.getMessageConsumerCount() == null ? 0 :
                    existMessageConsumerRecordVo.getMessageConsumerCount()) + 1;
        }
        UpdateMessageConsumerRecordDto updateMessageConsumerRecordDto = new UpdateMessageConsumerRecordDto();
        updateMessageConsumerRecordDto.setId(messageConsumerRecordId);
        updateMessageConsumerRecordDto.setMessageConsumerCount(messageConsumerCount);
        updateMessageConsumerRecordDto.setConsumerTime(DateUtils.now());
        
        try {
            OrderCancelDto orderCancelDto = new OrderCancelDto();
            orderCancelDto.setOrderNumber(orderNumber);
            boolean cancel = orderService.cancel(orderCancelDto);
            if (cancel) {
                log.info("延迟订单取消成功 orderCancelDto : {}",content);
                updateMessageConsumerRecordDto.setMessageConsumerStatus(MessageConsumerStatus.CONSUMER_SUCCESS.getCode());
            }else {
                log.error("延迟订单取消失败 orderCancelDto : {}",content);
                updateMessageConsumerRecordDto.setMessageConsumerStatus(MessageConsumerStatus.CONSUMER_FAIL.getCode());
                updateMessageConsumerRecordDto.setMessageConsumerException("订单取消失败");
            }
        } catch (TikectsystemFrameException e) {
            updateMessageConsumerRecordDto.setMessageConsumerStatus(MessageConsumerStatus.CONSUMER_SUCCESS.getCode());
        } catch (Exception e) {
            updateMessageConsumerRecordDto.setMessageConsumerStatus(MessageConsumerStatus.CONSUMER_FAIL.getCode());
            updateMessageConsumerRecordDto.setMessageConsumerException(e.getMessage());
        }
        apiDataClient.updateMessageConsumerRecord(updateMessageConsumerRecordDto);
    }
    
    @Override
    public String topic() {
        return SpringUtil.getPrefixDistinctionName() + "-" + DELAY_ORDER_CANCEL_TOPIC;
    }
}
