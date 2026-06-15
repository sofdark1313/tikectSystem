package com.tikectsystem.service.delayconsumer;

import com.tikectsystem.ThreadLocalTaskWrapper;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 过期订单取消专用执行器，隔离Kafka等待调度和取消执行任务。
 */
@Component
public class DelayOrderCancelTaskExecutor {

    private final ScheduledThreadPoolExecutor scheduler;

    private final ThreadPoolExecutor worker;

    public DelayOrderCancelTaskExecutor(
            @Value("${delay.order.cancel.executor.schedule-pool-size:8}") Integer schedulePoolSize,
            @Value("${delay.order.cancel.executor.worker-core-pool-size:16}") Integer workerCorePoolSize,
            @Value("${delay.order.cancel.executor.worker-maximum-pool-size:64}") Integer workerMaximumPoolSize,
            @Value("${delay.order.cancel.executor.worker-queue-capacity:5000}") Integer workerQueueCapacity) {
        this.scheduler = new ScheduledThreadPoolExecutor(
                Math.max(1, schedulePoolSize),
                new NamedThreadFactory("delay-order-cancel-schedule"));
        this.scheduler.setRemoveOnCancelPolicy(true);
        int coreSize = Math.max(1, workerCorePoolSize);
        int maxSize = Math.max(coreSize, workerMaximumPoolSize);
        this.worker = new ThreadPoolExecutor(
                coreSize,
                maxSize,
                60,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(Math.max(1, workerQueueCapacity)),
                new NamedThreadFactory("delay-order-cancel-worker"),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    public void schedule(Runnable task, long delayMillis) {
        schedule(task, delayMillis, TimeUnit.MILLISECONDS,
                ThreadLocalTaskWrapper.copyCurrentMdcContext(), ThreadLocalTaskWrapper.copyCurrentHoldContext());
    }

    public void schedule(Runnable task, long delay, TimeUnit timeUnit, Map<String, String> mdcContext, Map<String, String> holdContext) {
        scheduler.schedule(ThreadLocalTaskWrapper.wrap(task, mdcContext, holdContext), Math.max(0, delay), timeUnit);
    }

    public void execute(Runnable task) {
        execute(task, ThreadLocalTaskWrapper.copyCurrentMdcContext(), ThreadLocalTaskWrapper.copyCurrentHoldContext());
    }

    public void execute(Runnable task, Map<String, String> mdcContext, Map<String, String> holdContext) {
        worker.execute(ThreadLocalTaskWrapper.wrap(task, mdcContext, holdContext));
    }

    @PreDestroy
    public void destroy() {
        scheduler.shutdown();
        worker.shutdown();
    }

    private static class NamedThreadFactory implements ThreadFactory {
        private final String namePrefix;
        private final AtomicInteger threadIndex = new AtomicInteger(1);

        private NamedThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, namePrefix + "-" + threadIndex.getAndIncrement());
            thread.setDaemon(false);
            return thread;
        }
    }
}
