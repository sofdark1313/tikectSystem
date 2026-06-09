package com.tikectsystem.base;

import com.tikectsystem.ThreadLocalTaskWrapper;

import java.util.Map;
import java.util.concurrent.Callable;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 线程池基类
 * @author: 阿星不是程序员
 **/
public class BaseThreadPool {
    
    protected static Map<String, String> getContextForTask() {
        return ThreadLocalTaskWrapper.copyCurrentMdcContext();
    }
   
    protected static Map<String,String> getContextForHold() {
        return ThreadLocalTaskWrapper.copyCurrentHoldContext();
    }
   
    protected static Runnable wrapTask(final Runnable runnable, final Map<String, String> parentMdcContext, final Map<String, String> parentHoldContext) {
        return ThreadLocalTaskWrapper.wrap(runnable, parentMdcContext, parentHoldContext);
    }
    
    protected static <T> Callable<T> wrapTask(Callable<T> task, final Map<String, String> parentMdcContext, final Map<String, String> parentHoldContext) {
        return ThreadLocalTaskWrapper.wrap(task, parentMdcContext, parentHoldContext);
    }
}
