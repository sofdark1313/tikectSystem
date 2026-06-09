package com.tikectsystem.service.delayconsumer;

import com.alibaba.fastjson.JSON;
import com.tikectsystem.BusinessThreadPool;
import com.tikectsystem.client.ApiDataClient;
import com.tikectsystem.common.ApiResponse;
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
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.tikectsystem.constant.Constant.SPRING_INJECT_PREFIX_DISTINCTION_NAME;
import static com.tikectsystem.service.constant.OrderConstant.DELAY_ORDER_CANCEL_TOPIC;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 延迟订单取消
 * @author: 阿星不是程序员
 **/
@Slf4j
@Component
public class DelayOrderCancelConsumer {
    
    @Autowired
    private OrderService orderService;
    
    @Autowired
    private ApiDataClient apiDataClient;
    
    @Value("${delay.order.cancel.kafka.max-pending-task-count:100000}")
    private Integer maxPendingDelayTaskCount;

    private final AtomicInteger delayOrderCancelThreadIndex = new AtomicInteger(1);

    private final AtomicInteger pendingDelayOrderCancelCount = new AtomicInteger(0);

    private final ScheduledExecutorService delayOrderCancelScheduler = Executors.newScheduledThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors()),
            runnable -> {
                Thread thread = new Thread(runnable, "delay-order-cancel-kafka-" + delayOrderCancelThreadIndex.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            });

    @KafkaListener(topics = {SPRING_INJECT_PREFIX_DISTINCTION_NAME+"-"+DELAY_ORDER_CANCEL_TOPIC},
            containerFactory = "delayOrderCancelKafkaListenerContainerFactory")
    public void consumerDelayOrderCancelMessage(ConsumerRecord<String,String> consumerRecord) {
        if (consumerRecord == null || StringUtil.isEmpty(consumerRecord.value())) {
            return;
        }
        String value = consumerRecord.value();
        DelayOrderCancelMessageModule delayOrderCancelMessageModule;
        try {
            delayOrderCancelMessageModule = JSON.parseObject(value, DelayOrderCancelMessageModule.class);
        } catch (Exception e) {
            log.error("延迟订单取消Kafka消息解析失败 value : {}", value, e);
            return;
        }
        if (delayOrderCancelMessageModule == null || delayOrderCancelMessageModule.getMessageId() == null ||
                delayOrderCancelMessageModule.getOrderNumber() == null) {
            log.error("延迟订单取消Kafka消息格式错误 value : {}", value);
            return;
        }
        Long executeTimestamp = delayOrderCancelMessageModule.getExecuteTimestamp();
        long delayMillis = executeTimestamp == null ? 0L : executeTimestamp - System.currentTimeMillis();
        if (delayMillis <= 0) {
            asyncExecute(value);
            return;
        }
        try {
            log.debug("延迟订单取消Kafka消息等待执行 orderNumber : {}, delayMillis : {}",
                    delayOrderCancelMessageModule.getOrderNumber(), delayMillis);
            scheduleDelayExecute(value, delayOrderCancelMessageModule.getOrderNumber(), delayMillis);
        } catch (Exception e) {
            log.error("延迟订单取消Kafka消息调度失败 value : {}", value, e);
        }
    }

    private void scheduleDelayExecute(String content, Long orderNumber, long delayMillis) {
        int pendingCount = pendingDelayOrderCancelCount.incrementAndGet();
        if (pendingCount > Math.max(1, maxPendingDelayTaskCount)) {
            pendingDelayOrderCancelCount.decrementAndGet();
            log.warn("延迟订单取消本地等待任务过多，交由消息对账补偿重投 orderNumber : {}, pendingCount : {}",
                    orderNumber, pendingCount);
            return;
        }
        try {
            delayOrderCancelScheduler.schedule(() -> {
                try {
                    asyncExecute(content);
                } finally {
                    pendingDelayOrderCancelCount.decrementAndGet();
                }
            }, delayMillis, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            pendingDelayOrderCancelCount.decrementAndGet();
            throw e;
        }
    }

    private void asyncExecute(String content) {
        try {
            BusinessThreadPool.execute(() -> {
                try {
                    execute(content);
                } catch (Exception e) {
                    log.error("延迟订单取消任务执行失败 content : {}", content, e);
                }
            });
        } catch (Exception e) {
            log.error("延迟订单取消任务提交失败 content : {}", content, e);
        }
    }

    @PreDestroy
    public void destroy() {
        delayOrderCancelScheduler.shutdown();
    }
    
    public void execute(String content) {
        log.debug("延迟订单取消消息进行消费 content : {}", content);
        if (StringUtil.isEmpty(content)) {
            log.error("延迟订单取消消息不存在");
            return;
        }
        DelayOrderCancelMessageModule delayOrderCancelMessageModule;
        try {
            delayOrderCancelMessageModule = JSON.parseObject(content, DelayOrderCancelMessageModule.class);
        } catch (Exception e) {
            log.error("延迟订单取消消息解析失败 content : {}", content, e);
            return;
        }
        if (delayOrderCancelMessageModule == null) {
            log.error("延迟订单取消消息格式错误 content : {}", content);
            return;
        }
        
        Long messageTraceId = delayOrderCancelMessageModule.getMessageTraceId();
        Long messageId = delayOrderCancelMessageModule.getMessageId();
        Long programId = delayOrderCancelMessageModule.getProgramId();
        Long orderNumber = delayOrderCancelMessageModule.getOrderNumber();
        if (messageId == null || orderNumber == null) {
            log.error("延迟订单取消消息缺少必要参数 content : {}", content);
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
}
