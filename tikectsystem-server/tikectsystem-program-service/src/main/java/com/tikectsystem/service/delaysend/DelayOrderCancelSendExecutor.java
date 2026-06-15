package com.tikectsystem.service.delaysend;

import com.tikectsystem.ThreadLocalTaskWrapper;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 过期订单取消消息发送专用线程池。
 */
@Component
public class DelayOrderCancelSendExecutor {

    private final ThreadPoolExecutor executor;

    public DelayOrderCancelSendExecutor(
            @Value("${delay.order.cancel-send.core-pool-size:4}") Integer corePoolSize,
            @Value("${delay.order.cancel-send.maximum-pool-size:16}") Integer maximumPoolSize,
            @Value("${delay.order.cancel-send.queue-capacity:2000}") Integer queueCapacity) {
        int coreSize = Math.max(1, corePoolSize);
        int maxSize = Math.max(coreSize, maximumPoolSize);
        this.executor = new ThreadPoolExecutor(
                coreSize,
                maxSize,
                60,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(Math.max(1, queueCapacity)),
                new DelayOrderCancelSendThreadFactory(),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    public void execute(Runnable task) {
        executor.execute(ThreadLocalTaskWrapper.wrap(task));
    }

    @PreDestroy
    public void destroy() {
        executor.shutdown();
    }

    private static class DelayOrderCancelSendThreadFactory implements ThreadFactory {
        private final AtomicInteger threadIndex = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "delay-order-cancel-send-" + threadIndex.getAndIncrement());
            thread.setDaemon(false);
            return thread;
        }
    }
}
