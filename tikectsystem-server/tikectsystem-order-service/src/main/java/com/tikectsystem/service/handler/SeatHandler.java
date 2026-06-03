package com.tikectsystem.service.handler;

import com.tikectsystem.core.RedisKeyManage;
import com.tikectsystem.redis.RedisCache;
import com.tikectsystem.redis.RedisKeyBuild;
import com.tikectsystem.servicelock.LockType;
import com.tikectsystem.servicelock.annotion.ServiceLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.tikectsystem.core.DistributedLockConstants.SEAT_LOCK;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 座位处理
 * @author: 阿星不是程序员
 **/
@Slf4j
@Component
public class SeatHandler {
    
    @Autowired
    private RedisCache redisCache;

    /**
     * 从redis中删除座位数据
     * */
    @ServiceLock(lockType= LockType.Write,name = SEAT_LOCK,keys = {"#programId","#ticketCategoryId"})
    public void delRedisSeatData(Long programId,Long ticketCategoryId){
        List<RedisKeyBuild> keyList = new ArrayList<>();
        keyList.add(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_SEAT_NO_SOLD_RESOLUTION_HASH,programId,ticketCategoryId));
        keyList.add(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_SEAT_LOCK_RESOLUTION_HASH,programId,ticketCategoryId));
        keyList.add(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_SEAT_SOLD_RESOLUTION_HASH,programId,ticketCategoryId));
        redisCache.del(keyList);
    }
}
