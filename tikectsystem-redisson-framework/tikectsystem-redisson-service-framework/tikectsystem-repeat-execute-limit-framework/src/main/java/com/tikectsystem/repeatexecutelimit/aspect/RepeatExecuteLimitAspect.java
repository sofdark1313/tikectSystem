package com.tikectsystem.repeatexecutelimit.aspect;

import com.tikectsystem.constant.LockInfoType;
import com.tikectsystem.exception.TikectsystemFrameException;
import com.tikectsystem.handle.RedissonDataHandle;
import com.tikectsystem.locallock.LocalLockCache;
import com.tikectsystem.lockinfo.LockInfoHandle;
import com.tikectsystem.lockinfo.factory.LockInfoHandleFactory;
import com.tikectsystem.repeatexecutelimit.annotion.RepeatExecuteLimit;
import com.tikectsystem.servicelock.LockType;
import com.tikectsystem.servicelock.ServiceLocker;
import com.tikectsystem.servicelock.factory.ServiceLockFactory;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static com.tikectsystem.repeatexecutelimit.constant.RepeatExecuteLimitConstant.PREFIX_NAME;
import static com.tikectsystem.repeatexecutelimit.constant.RepeatExecuteLimitConstant.SUCCESS_FLAG;

/**
 /**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 防重复幂等 切面
 * @author: 阿星不是程序员
 **/
@Slf4j
@Aspect
@Order(-11)
@AllArgsConstructor
public class RepeatExecuteLimitAspect {

    private final LocalLockCache localLockCache;

    private final LockInfoHandleFactory lockInfoHandleFactory;

    private final ServiceLockFactory serviceLockFactory;

    private final RedissonDataHandle redissonDataHandle;


    @Around("@annotation(repeatLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RepeatExecuteLimit repeatLimit) throws Throwable {
        //指定保持幂等的时间
        long durationTime = repeatLimit.durationTime();
        //提示信息
        String message = repeatLimit.message();
        Object obj;
        //获取锁信息
        LockInfoHandle lockInfoHandle = lockInfoHandleFactory.getLockInfoHandle(LockInfoType.REPEAT_EXECUTE_LIMIT);
        //解析锁名字
        String lockName = lockInfoHandle.getLockName(joinPoint,repeatLimit.name(), repeatLimit.keys());
        //幂等标识
        String repeatFlagName = PREFIX_NAME + lockName;
        //获得幂等标识
        String flagObject = redissonDataHandle.get(repeatFlagName);
        //如果幂等标识的值为success，说明已经有请求在执行了，这次请求直接结束
        if (SUCCESS_FLAG.equals(flagObject)) {
            throw new TikectsystemFrameException(message);
        }
        //获取本地锁
        ReentrantLock localLock = localLockCache.getLock(lockName,false);
        //本地锁获取锁
        boolean localLockResult = localLock.tryLock();
        //如果上锁失败，说明已经有请求在执行了，这次请求直接结束
        if (!localLockResult) {
            throw new TikectsystemFrameException(message);
        }
        try {
            //获取分布式锁
            ServiceLocker lock = serviceLockFactory.getLock(LockType.Reentrant);
            //分布式锁获取锁
            boolean result = lock.tryLock(lockName, TimeUnit.SECONDS, 0);
            //加锁成功执行
            if (result) {
                try{
                    //再次获取幂等标识
                    flagObject = redissonDataHandle.get(repeatFlagName);
                    //如果幂等标识的值为success，说明已经有请求在执行了，这次请求直接结束
                    if (SUCCESS_FLAG.equals(flagObject)) {
                        throw new TikectsystemFrameException(message);
                    }
                    //执行业务逻辑
                    obj = joinPoint.proceed();
                    if (durationTime > 0) {
                        try {
                            //业务逻辑执行成功 并且 指定了设置幂等保持时间 设置请求标识
                            redissonDataHandle.set(repeatFlagName,SUCCESS_FLAG,durationTime,TimeUnit.SECONDS);
                        }catch (Exception e) {
                            log.error("getBucket error",e);
                        }
                    }
                    return obj;
                } finally {
                    lock.unlock(lockName);
                }
            }else{
                //获取锁失败，说明已经有请求在执行了，这次请求直接结束
                throw new TikectsystemFrameException(message);
            }
        }finally {
            localLock.unlock();
        }
    }
}