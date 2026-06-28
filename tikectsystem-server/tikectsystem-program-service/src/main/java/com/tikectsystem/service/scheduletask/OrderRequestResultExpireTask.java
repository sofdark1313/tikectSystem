package com.tikectsystem.service.scheduletask;

import com.tikectsystem.BusinessThreadPool;
import com.tikectsystem.service.OrderRequestResultService;
import com.tikectsystem.util.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 异步下单请求结果补偿任务。
 */
@Slf4j
@Component
public class OrderRequestResultExpireTask {

    private static final int STUCK_PROCESSING_MINUTES = 5;

    private static final int EXPIRE_BATCH_SIZE = 200;

    @Autowired
    private OrderRequestResultService orderRequestResultService;

    /**
     * 过期长时间停留在 PROCESSING 的请求，防止客户端一直轮询中间态。
     */
    @Scheduled(cron = "0 0/1 * * * ? ")
    public void expireStuckProcessing() {
        BusinessThreadPool.execute(() -> {
            try {
                int expireCount = orderRequestResultService.expireStuckProcessing(
                        DateUtils.addMinute(DateUtils.now(), -STUCK_PROCESSING_MINUTES), EXPIRE_BATCH_SIZE);
                if (expireCount > 0) {
                    log.warn("expire stuck order request processing records, count:{}", expireCount);
                }
            } catch (Exception e) {
                log.error("expire stuck order request processing error", e);
            }
        });
    }
}
