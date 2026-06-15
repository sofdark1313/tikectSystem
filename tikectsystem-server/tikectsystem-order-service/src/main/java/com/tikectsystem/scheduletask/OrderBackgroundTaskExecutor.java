package com.tikectsystem.scheduletask;

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
 * 订单服务后台定时任务专用线程池。
 */
@Component
public class OrderBackgroundTaskExecutor {

    private final ThreadPoolExecutor executor;

    public OrderBackgroundTaskExecutor(
            @Value("${order.background-task.core-pool-size:2}") Integer corePoolSize,
            @Value("${order.background-task.maximum-pool-size:8}") Integer maximumPoolSize,
            @Value("${order.background-task.queue-capacity:200}") Integer queueCapacity) {
        int coreSize = Math.max(1, corePoolSize);
        int maxSize = Math.max(coreSize, maximumPoolSize);
        this.executor = new ThreadPoolExecutor(
                coreSize,
                maxSize,
                60,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(Math.max(1, queueCapacity)),
                new NamedThreadFactory(),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    public void execute(Runnable task) {
        executor.execute(ThreadLocalTaskWrapper.wrap(task));
    }

    @PreDestroy
    public void destroy() {
        executor.shutdown();
    }

    private static class NamedThreadFactory implements ThreadFactory {
        private final AtomicInteger threadIndex = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "order-background-task-" + threadIndex.getAndIncrement());
            thread.setDaemon(false);
            return thread;
        }
    }
}
