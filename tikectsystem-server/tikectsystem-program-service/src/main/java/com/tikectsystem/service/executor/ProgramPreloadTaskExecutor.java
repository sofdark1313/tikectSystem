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
 * 节目详情预热专用线程池。
 */
@Component
public class ProgramPreloadTaskExecutor {

    private final ThreadPoolExecutor executor;

    public ProgramPreloadTaskExecutor(
            @Value("${program.preload-task.core-pool-size:4}") Integer corePoolSize,
            @Value("${program.preload-task.maximum-pool-size:12}") Integer maximumPoolSize,
            @Value("${program.preload-task.queue-capacity:1000}") Integer queueCapacity) {
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
            Thread thread = new Thread(runnable, "program-preload-task-" + threadIndex.getAndIncrement());
            thread.setDaemon(false);
            return thread;
        }
    }
}
