package com.tikectsystem.service.delayconsumer;

import com.alibaba.fastjson.JSON;
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
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.tikectsystem.constant.Constant.SPRING_INJECT_PREFIX_DISTINCTION_NAME;
import static com.tikectsystem.constant.Constant.TRACE_ID;
import static com.tikectsystem.service.constant.OrderConstant.DELAY_ORDER_CANCEL_TOPIC;

/**
 * @program: 鏋佸害鐪熷疄杩樺師澶ч害缃戦珮骞跺彂瀹炴垬椤圭洰銆?娣诲姞 闃挎槦涓嶆槸绋嬪簭鍛?寰俊锛屾坊鍔犳椂澶囨敞 澶ч害 鏉ヨ幏鍙栭」鐩殑瀹屾暣璧勬枡
 * @description: 寤惰繜璁㈠崟鍙栨秷
 * @author: 闃挎槦涓嶆槸绋嬪簭鍛?
 **/
@Slf4j
@Component
public class DelayOrderCancelConsumer {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ApiDataClient apiDataClient;

    @Autowired
    private DelayOrderCancelTaskExecutor delayOrderCancelTaskExecutor;

    @Value("${delay.order.cancel.kafka.max-pending-task-count:100000}")
    private Integer maxPendingDelayTaskCount;

    private final AtomicInteger pendingDelayOrderCancelCount = new AtomicInteger(0);

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
            log.error("寤惰繜璁㈠崟鍙栨秷Kafka娑堟伅瑙ｆ瀽澶辫触 value : {}", value, e);
            return;
        }
        if (delayOrderCancelMessageModule == null || delayOrderCancelMessageModule.getMessageId() == null ||
                delayOrderCancelMessageModule.getOrderNumber() == null) {
            log.error("寤惰繜璁㈠崟鍙栨秷Kafka娑堟伅鏍煎紡閿欒 value : {}", value);
            return;
        }
        Long executeTimestamp = delayOrderCancelMessageModule.getExecuteTimestamp();
        long delayMillis = executeTimestamp == null ? 0L : executeTimestamp - System.currentTimeMillis();
        Map<String, String> traceContext = buildTraceContext(delayOrderCancelMessageModule);
        if (delayMillis <= 0) {
            asyncExecute(value, traceContext);
            return;
        }
        try {
            log.debug("寤惰繜璁㈠崟鍙栨秷Kafka娑堟伅绛夊緟鎵ц orderNumber : {}, delayMillis : {}",
                    delayOrderCancelMessageModule.getOrderNumber(), delayMillis);
            scheduleDelayExecute(value, delayOrderCancelMessageModule, delayMillis, traceContext);
        } catch (Exception e) {
            log.error("寤惰繜璁㈠崟鍙栨秷Kafka娑堟伅璋冨害澶辫触 value : {}", value, e);
        }
    }

    private void scheduleDelayExecute(String content, DelayOrderCancelMessageModule messageModule, long delayMillis,
                                      Map<String, String> traceContext) {
        int pendingCount = pendingDelayOrderCancelCount.incrementAndGet();
        if (pendingCount > Math.max(1, maxPendingDelayTaskCount)) {
            pendingDelayOrderCancelCount.decrementAndGet();
            log.warn("寤惰繜璁㈠崟鍙栨秷鏈湴绛夊緟浠诲姟杩囧锛屼氦鐢辨秷鎭璐﹁ˉ鍋块噸鎶?orderNumber : {}, pendingCount : {}",
                    messageModule.getOrderNumber(), pendingCount);
            return;
        }
        try {
            delayOrderCancelTaskExecutor.schedule(() -> {
                try {
                    asyncExecute(content, traceContext);
                } finally {
                    pendingDelayOrderCancelCount.decrementAndGet();
                }
            }, delayMillis, TimeUnit.MILLISECONDS, traceContext, traceContext);
        } catch (Exception e) {
            pendingDelayOrderCancelCount.decrementAndGet();
            throw e;
        }
    }

    private void asyncExecute(String content, Map<String, String> traceContext) {
        try {
            delayOrderCancelTaskExecutor.execute(() -> {
                try {
                    execute(content);
                } catch (Exception e) {
                    log.error("寤惰繜璁㈠崟鍙栨秷浠诲姟鎵ц澶辫触 content : {}", content, e);
                }
            }, traceContext, traceContext);
        } catch (Exception e) {
            log.error("寤惰繜璁㈠崟鍙栨秷浠诲姟鎻愪氦澶辫触 content : {}", content, e);
        }
    }

    private Map<String, String> buildTraceContext(DelayOrderCancelMessageModule messageModule) {
        Map<String, String> context = new HashMap<>(4);
        if (messageModule != null && messageModule.getMessageTraceId() != null) {
            context.put(TRACE_ID, String.valueOf(messageModule.getMessageTraceId()));
        }
        return context;
    }

    public void execute(String content) {
        log.debug("寤惰繜璁㈠崟鍙栨秷娑堟伅杩涜娑堣垂 content : {}", content);
        if (StringUtil.isEmpty(content)) {
            log.error("延迟订单取消消息不存在");
            return;
        }
        DelayOrderCancelMessageModule delayOrderCancelMessageModule;
        try {
            delayOrderCancelMessageModule = JSON.parseObject(content, DelayOrderCancelMessageModule.class);
        } catch (Exception e) {
            log.error("寤惰繜璁㈠崟鍙栨秷娑堟伅瑙ｆ瀽澶辫触 content : {}", content, e);
            return;
        }
        if (delayOrderCancelMessageModule == null) {
            log.error("寤惰繜璁㈠崟鍙栨秷娑堟伅鏍煎紡閿欒 content : {}", content);
            return;
        }

        Long messageTraceId = delayOrderCancelMessageModule.getMessageTraceId();
        Long messageId = delayOrderCancelMessageModule.getMessageId();
        Long programId = delayOrderCancelMessageModule.getProgramId();
        Long orderNumber = delayOrderCancelMessageModule.getOrderNumber();
        if (messageId == null || orderNumber == null) {
            log.error("寤惰繜璁㈠崟鍙栨秷娑堟伅缂哄皯蹇呰鍙傛暟 content : {}", content);
            return;
        }

        MessageIdDto messageIdDto = new MessageIdDto();
        messageIdDto.setMessageId(messageId);
        ApiResponse<MessageConsumerRecordVo> apiResponse = apiDataClient.getMessageConsumerByMessageId(messageIdDto);
        if (apiResponse == null || !Objects.equals(apiResponse.getCode(),BaseCode.SUCCESS.getCode())) {
            log.error("鏌ヨ娑堟伅娑堣垂璁板綍澶辫触 messageId : {}",messageId);
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
                log.error("娣诲姞娑堟伅娑堣垂璁板綍澶辫触 insertMessageConsumerRecordDto : {}", JSON.toJSONString(insertMessageConsumerRecordDto));
                return;
            }
            MessageConsumerRecordVo saveMessageConsumerRecordVo = insertApiResponse.getData();
            if (saveMessageConsumerRecordVo == null || saveMessageConsumerRecordVo.getId() == null) {
                log.error("娣诲姞娑堟伅娑堣垂璁板綍杩斿洖鏁版嵁涓虹┖ insertMessageConsumerRecordDto : {}", JSON.toJSONString(insertMessageConsumerRecordDto));
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
                log.info("寤惰繜璁㈠崟鍙栨秷鎴愬姛 orderCancelDto : {}",content);
                updateMessageConsumerRecordDto.setMessageConsumerStatus(MessageConsumerStatus.CONSUMER_SUCCESS.getCode());
            }else {
                log.error("寤惰繜璁㈠崟鍙栨秷澶辫触 orderCancelDto : {}",content);
                updateMessageConsumerRecordDto.setMessageConsumerStatus(MessageConsumerStatus.CONSUMER_FAIL.getCode());
                updateMessageConsumerRecordDto.setMessageConsumerException("璁㈠崟鍙栨秷澶辫触");
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
