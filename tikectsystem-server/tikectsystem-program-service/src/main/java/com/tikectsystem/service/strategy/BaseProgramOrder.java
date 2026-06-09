package com.tikectsystem.service.strategy;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.tikectsystem.dto.ProgramOrderCreateDto;
import com.tikectsystem.dto.SeatDto;
import com.tikectsystem.locallock.LocalLockCache;
import com.tikectsystem.lock.LockTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Component
public class BaseProgramOrder {
    
    @Autowired
    private LocalLockCache localLockCache;

    public String localLockCreateOrder(String lockKeyPrefix,ProgramOrderCreateDto programOrderCreateDto,LockTask<String> lockTask){
        return localLockExecute(lockKeyPrefix, programOrderCreateDto, lockTask);
    }

    public <T> T localLockExecute(String lockKeyPrefix,ProgramOrderCreateDto programOrderCreateDto,LockTask<T> lockTask){
        Set<Long> ticketCategoryIdSet = new TreeSet<>();
        List<SeatDto> seatDtoList = programOrderCreateDto.getSeatDtoList();
        if (CollectionUtil.isNotEmpty(seatDtoList)) {
            for (SeatDto seatDto : seatDtoList) {
                ticketCategoryIdSet.add(seatDto.getTicketCategoryId());
            }
        } else {
            ticketCategoryIdSet.add(programOrderCreateDto.getTicketCategoryId());
        }
        List<ReentrantLock> localLockSuccessList = new ArrayList<>(ticketCategoryIdSet.size());
        try {
            for (Long ticketCategoryId : ticketCategoryIdSet) {
                String lockKey = StrUtil.join("-",lockKeyPrefix,
                        programOrderCreateDto.getProgramId(),ticketCategoryId);
                ReentrantLock localLock = localLockCache.getLock(lockKey,false);
                localLock.lock();
                localLockSuccessList.add(localLock);
            }
            return lockTask.execute();
        } finally {
            for (int i = localLockSuccessList.size() - 1; i >= 0; i--) {
                ReentrantLock reentrantLock = localLockSuccessList.get(i);
                try {
                    reentrantLock.unlock();
                } catch (Throwable t) {
                    log.error("local lock unlock error",t);
                }
            }
        }
    }
}
