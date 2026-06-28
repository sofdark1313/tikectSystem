package com.tikectsystem.service.delayconsumer;

import com.alibaba.fastjson.JSON;
import com.tikectsystem.dto.OrderCancelDto;
import com.tikectsystem.enums.BaseCode;
import com.tikectsystem.exception.TikectsystemFrameException;
import com.tikectsystem.module.DelayOrderCancelMessageModule;
import com.tikectsystem.service.OrderService;
import com.tikectsystem.util.StringUtil;
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
 * 延迟取消订单消费端。
 */
@Slf4j
@Component
public class DelayOrderCancelConsumer {

    @Autowired
    private OrderService orderService;

    @Autowired
    private DelayOrderCancelTaskExecutor delayOrderCancelTaskExecutor;

    @Value("${delay.order.cancel.kafka.max-pending-task-count:100000}")
    private Integer maxPendingDelayTaskCount;

    private final AtomicInteger pendingDelayOrderCancelCount = new AtomicInteger(0);

    /**
     * 消费延迟取消消息，到期后异步执行订单取消。
     *
     * @param consumerRecord Kafka 消息
     */
    @KafkaListener(topics = {SPRING_INJECT_PREFIX_DISTINCTION_NAME + "-" + DELAY_ORDER_CANCEL_TOPIC},
            containerFactory = "delayOrderCancelKafkaListenerContainerFactory")
    public void consumerDelayOrderCancelMessage(ConsumerRecord<String, String> consumerRecord) {
        if (consumerRecord == null || StringUtil.isEmpty(consumerRecord.value())) {
            return;
        }
        String value = consumerRecord.value();
        DelayOrderCancelMessageModule messageModule;
        try {
            messageModule = JSON.parseObject(value, DelayOrderCancelMessageModule.class);
        } catch (Exception e) {
            log.error("delay order cancel message parse error, value:{}", value, e);
            return;
        }
        if (messageModule == null || messageModule.getMessageId() == null || messageModule.getOrderNumber() == null) {
            log.error("delay order cancel message format error, value:{}", value);
            return;
        }

        Long executeTimestamp = messageModule.getExecuteTimestamp();
        long delayMillis = executeTimestamp == null ? 0L : executeTimestamp - System.currentTimeMillis();
        Map<String, String> traceContext = buildTraceContext(messageModule);
        if (delayMillis <= 0) {
            asyncExecute(value, traceContext);
            return;
        }
        try {
            log.debug("delay order cancel scheduled, orderNumber:{}, delayMillis:{}",
                    messageModule.getOrderNumber(), delayMillis);
            scheduleDelayExecute(value, messageModule, delayMillis, traceContext);
        } catch (Exception e) {
            log.error("delay order cancel schedule failed, value:{}", value, e);
        }
    }

    private void scheduleDelayExecute(String content, DelayOrderCancelMessageModule messageModule, long delayMillis,
                                      Map<String, String> traceContext) {
        int pendingCount = pendingDelayOrderCancelCount.incrementAndGet();
        if (pendingCount > Math.max(1, maxPendingDelayTaskCount)) {
            pendingDelayOrderCancelCount.decrementAndGet();
            log.warn("delay order cancel pending task limit reached, orderNumber:{}, pendingCount:{}",
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
            delayOrderCancelTaskExecutor.execute(() -> execute(content), traceContext, traceContext);
        } catch (Exception e) {
            log.error("delay order cancel async submit failed, content:{}", content, e);
        }
    }

    private Map<String, String> buildTraceContext(DelayOrderCancelMessageModule messageModule) {
        Map<String, String> context = new HashMap<>(4);
        if (messageModule != null && messageModule.getMessageTraceId() != null) {
            context.put(TRACE_ID, String.valueOf(messageModule.getMessageTraceId()));
        }
        return context;
    }

    /**
     * 执行延迟取消。幂等由订单取消接口的订单号锁和状态校验承担，不再同步写消费记录表。
     *
     * @param content 延迟取消消息体
     */
    public void execute(String content) {
        if (StringUtil.isEmpty(content)) {
            log.error("delay order cancel content is empty");
            return;
        }
        DelayOrderCancelMessageModule messageModule;
        try {
            messageModule = JSON.parseObject(content, DelayOrderCancelMessageModule.class);
        } catch (Exception e) {
            log.error("delay order cancel content parse error, content:{}", content, e);
            return;
        }
        if (messageModule == null || messageModule.getMessageId() == null || messageModule.getOrderNumber() == null) {
            log.error("delay order cancel content format error, content:{}", content);
            return;
        }

        OrderCancelDto orderCancelDto = new OrderCancelDto();
        orderCancelDto.setOrderNumber(messageModule.getOrderNumber());
        try {
            boolean cancel = orderService.cancel(orderCancelDto);
            if (cancel) {
                log.info("delay order cancel success, orderNumber:{}", messageModule.getOrderNumber());
            } else {
                log.error("delay order cancel returned false, orderNumber:{}", messageModule.getOrderNumber());
            }
        } catch (TikectsystemFrameException e) {
            if (!Objects.equals(e.getCode(), BaseCode.ORDER_CANCEL.getCode()) &&
                    !Objects.equals(e.getCode(), BaseCode.ORDER_NOT_EXIST.getCode())) {
                log.warn("delay order cancel business exception, orderNumber:{}",
                        messageModule.getOrderNumber(), e);
            }
        } catch (Exception e) {
            log.error("delay order cancel execute failed, orderNumber:{}", messageModule.getOrderNumber(), e);
            throw e;
        }
    }
}
