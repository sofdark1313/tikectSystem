package com.tikectsystem.config;

import com.tikectsystem.constant.LockInfoType;
import com.tikectsystem.handle.RedissonDataHandle;
import com.tikectsystem.locallock.LocalLockCache;
import com.tikectsystem.lockinfo.LockInfoHandle;
import com.tikectsystem.lockinfo.factory.LockInfoHandleFactory;
import com.tikectsystem.lockinfo.impl.RepeatExecuteLimitLockInfoHandle;
import com.tikectsystem.repeatexecutelimit.aspect.RepeatExecuteLimitAspect;
import com.tikectsystem.servicelock.factory.ServiceLockFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 防重复幂等配置
 * @author: 阿星不是程序员
 **/
@Configuration
public class RepeatExecuteLimitAutoConfiguration {
    
    @Bean(LockInfoType.REPEAT_EXECUTE_LIMIT)
    public LockInfoHandle repeatExecuteLimitHandle(){
        return new RepeatExecuteLimitLockInfoHandle();
    }
    
    @Bean
    public RepeatExecuteLimitAspect repeatExecuteLimitAspect(LocalLockCache localLockCache,
                                                             LockInfoHandleFactory lockInfoHandleFactory,
                                                             ServiceLockFactory serviceLockFactory,
                                                             RedissonDataHandle redissonDataHandle){
        return new RepeatExecuteLimitAspect(localLockCache, lockInfoHandleFactory,serviceLockFactory,redissonDataHandle);
    }
}
    