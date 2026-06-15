package com.tikectsystem;

import com.tikectsystem.threadlocal.BaseParameterHolder;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * 异步任务ThreadLocal上下文包装器，任务执行后恢复调用线程原上下文。
 */
public final class ThreadLocalTaskWrapper {

    private ThreadLocalTaskWrapper() {
    }

    public static Runnable wrap(Runnable runnable) {
        return wrap(runnable, MDC.getCopyOfContextMap(), BaseParameterHolder.getThreadLocal().get());
    }

    public static Runnable wrap(Runnable runnable, Map<String, String> mdcContext, Map<String, String> holdContext) {
        Map<String, String> parentMdcContext = copy(mdcContext);
        Map<String, String> parentHoldContext = copy(holdContext);
        return () -> runWithContext(parentMdcContext, parentHoldContext, runnable);
    }

    public static <T> Callable<T> wrap(Callable<T> task) {
        return wrap(task, MDC.getCopyOfContextMap(), BaseParameterHolder.getThreadLocal().get());
    }

    public static <T> Callable<T> wrap(Callable<T> task, Map<String, String> mdcContext, Map<String, String> holdContext) {
        Map<String, String> parentMdcContext = copy(mdcContext);
        Map<String, String> parentHoldContext = copy(holdContext);
        return () -> callWithContext(parentMdcContext, parentHoldContext, task);
    }

    public static void runWithContext(Map<String, String> mdcContext, Map<String, String> holdContext, Runnable runnable) {
        Map<String, String> oldMdcContext = copy(MDC.getCopyOfContextMap());
        Map<String, String> oldHoldContext = copy(BaseParameterHolder.getThreadLocal().get());
        try {
            setMdcContext(mdcContext);
            setHoldContext(holdContext);
            runnable.run();
        } finally {
            setMdcContext(oldMdcContext);
            setHoldContext(oldHoldContext);
        }
    }

    public static <T> T callWithContext(Map<String, String> mdcContext, Map<String, String> holdContext, Callable<T> task) throws Exception {
        Map<String, String> oldMdcContext = copy(MDC.getCopyOfContextMap());
        Map<String, String> oldHoldContext = copy(BaseParameterHolder.getThreadLocal().get());
        try {
            setMdcContext(mdcContext);
            setHoldContext(holdContext);
            return task.call();
        } finally {
            setMdcContext(oldMdcContext);
            setHoldContext(oldHoldContext);
        }
    }

    public static Map<String, String> copyCurrentMdcContext() {
        return copy(MDC.getCopyOfContextMap());
    }

    public static Map<String, String> copyCurrentHoldContext() {
        return copy(BaseParameterHolder.getThreadLocal().get());
    }

    public static Map<String, String> copy(Map<String, String> context) {
        return context == null ? null : new HashMap<>(context);
    }

    private static void setMdcContext(Map<String, String> context) {
        if (context == null || context.isEmpty()) {
            MDC.clear();
        } else {
            MDC.setContextMap(copy(context));
        }
    }

    private static void setHoldContext(Map<String, String> context) {
        if (context == null || context.isEmpty()) {
            BaseParameterHolder.removeParameterMap();
        } else {
            BaseParameterHolder.setParameterMap(copy(context));
        }
    }
}
