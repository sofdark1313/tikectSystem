package com.tikectsystem.service.scheduletask;

import com.tikectsystem.service.OrderRequestResultService;
import com.tikectsystem.util.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * V4 异步下单请求结果过期任务。
 * 用于清理长时间停留在 PROCESSING 的请求，避免前端重试一直复用不会推进的旧请求号。
 */
@Slf4j
@Component
public class OrderRequestResultExpireTask {

    /**
     * PROCESSING 请求超过该秒数仍未推进时允许过期。
     */
    @Value("${program.order.request-result.expire-before-seconds:60}")
    private Integer expireBeforeSeconds;

    /**
     * 单次最多过期的请求数量。
     */
    @Value("${program.order.request-result.expire-limit:200}")
    private Integer expireLimit;

    @Autowired
    private OrderRequestResultService orderRequestResultService;

    /**
     * 定时过期卡住的异步下单请求。
     */
    @Scheduled(fixedDelayString = "${program.order.request-result.expire-fixed-delay-ms:30000}")
    public void expireStuckProcessingOrderRequest() {
        try {
            int expireBeforeSecondsValue = Math.max(10, expireBeforeSeconds);
            int expireLimitValue = Math.max(1, expireLimit);
            int expireCount = orderRequestResultService.expireStuckProcessing(
                    DateUtils.addSecond(DateUtils.now(), -expireBeforeSecondsValue), expireLimitValue);
            if (expireCount > 0) {
                log.info("expire stuck order request result success, count : {}", expireCount);
            }
        } catch (Exception e) {
            log.error("expire stuck order request result failed", e);
        }
    }
}
