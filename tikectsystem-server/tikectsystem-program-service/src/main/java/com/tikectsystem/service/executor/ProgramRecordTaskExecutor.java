package com.tikectsystem.service.executor;

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
 * 下单后节目记录写入专用线程池，避免和通用后台任务争抢线程。
 */
@Component
public class ProgramRecordTaskExecutor {

    private final ThreadPoolExecutor executor;

    public ProgramRecordTaskExecutor(
            @Value("${program.order.record-task.core-pool-size:4}") Integer corePoolSize,
            @Value("${program.order.record-task.maximum-pool-size:16}") Integer maximumPoolSize,
            @Value("${program.order.record-task.queue-capacity:2000}") Integer queueCapacity) {
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
            Thread thread = new Thread(runnable, "program-record-task-" + threadIndex.getAndIncrement());
            thread.setDaemon(false);
            return thread;
        }
    }
}
