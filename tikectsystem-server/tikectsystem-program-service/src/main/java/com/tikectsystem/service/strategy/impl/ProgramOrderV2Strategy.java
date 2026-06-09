package com.tikectsystem.service.strategy.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.tikectsystem.core.RepeatExecuteLimitConstants;
import com.tikectsystem.dto.ProgramOrderCreateDto;
import com.tikectsystem.dto.SeatDto;
import com.tikectsystem.enums.BaseCode;
import com.tikectsystem.enums.CompositeCheckType;
import com.tikectsystem.enums.ProgramOrderVersion;
import com.tikectsystem.exception.TikectsystemFrameException;
import com.tikectsystem.initialize.base.AbstractApplicationCommandLineRunnerHandler;
import com.tikectsystem.initialize.impl.composite.CompositeContainer;
import com.tikectsystem.locallock.LocalLockCache;
import com.tikectsystem.repeatexecutelimit.annotion.RepeatExecuteLimit;
import com.tikectsystem.service.ProgramOrderService;
import com.tikectsystem.service.strategy.ProgramOrderContext;
import com.tikectsystem.service.strategy.ProgramOrderStrategy;
import com.tikectsystem.servicelock.LockType;
import com.tikectsystem.util.ServiceLockTool;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantLock;

import static com.tikectsystem.core.DistributedLockConstants.PROGRAM_ORDER_CREATE_V2;

@Slf4j
@Component
public class ProgramOrderV2Strategy extends AbstractApplicationCommandLineRunnerHandler implements ProgramOrderStrategy {
    
    @Autowired
    private ProgramOrderService programOrderService;
    
    @Autowired
    private ServiceLockTool serviceLockTool;
    
    @Autowired
    private CompositeContainer compositeContainer;
    
    @Autowired
    private LocalLockCache localLockCache;
    
    @RepeatExecuteLimit(
            name = RepeatExecuteLimitConstants.CREATE_PROGRAM_ORDER,
            keys = {"#programOrderCreateDto.userId","#programOrderCreateDto.programId"})
    @Override
    public String createOrder(ProgramOrderCreateDto programOrderCreateDto) {
        compositeContainer.execute(CompositeCheckType.PROGRAM_ORDER_CREATE_CHECK.getValue(),programOrderCreateDto);
        Set<Long> ticketCategoryIdSet = getTicketCategoryIdSet(programOrderCreateDto);
        List<ReentrantLock> localLockSuccessList = new ArrayList<>(ticketCategoryIdSet.size());
        List<RLock> serviceLockSuccessList = new ArrayList<>(ticketCategoryIdSet.size());
        try {
            for (Long ticketCategoryId : ticketCategoryIdSet) {
                String lockKey = buildLockKey(programOrderCreateDto, ticketCategoryId);
                ReentrantLock localLock = localLockCache.getLock(lockKey,false);
                localLock.lock();
                localLockSuccessList.add(localLock);
            }
            for (Long ticketCategoryId : ticketCategoryIdSet) {
                String lockKey = buildLockKey(programOrderCreateDto, ticketCategoryId);
                RLock serviceLock = serviceLockTool.getLock(LockType.Reentrant, lockKey);
                try {
                    serviceLock.lock();
                } catch (Throwable t) {
                    throw new TikectsystemFrameException(BaseCode.SERVICE_LOCK_FAIL);
                }
                serviceLockSuccessList.add(serviceLock);
            }
            return programOrderService.create(programOrderCreateDto,ProgramOrderVersion.V2_VERSION.getValue());
        } finally {
            unlockServiceLocks(serviceLockSuccessList);
            unlockLocalLocks(localLockSuccessList);
        }
    }

    private Set<Long> getTicketCategoryIdSet(ProgramOrderCreateDto programOrderCreateDto) {
        Set<Long> ticketCategoryIdSet = new TreeSet<>();
        List<SeatDto> seatDtoList = programOrderCreateDto.getSeatDtoList();
        if (CollectionUtil.isNotEmpty(seatDtoList)) {
            for (SeatDto seatDto : seatDtoList) {
                ticketCategoryIdSet.add(seatDto.getTicketCategoryId());
            }
        } else {
            ticketCategoryIdSet.add(programOrderCreateDto.getTicketCategoryId());
        }
        return ticketCategoryIdSet;
    }

    private String buildLockKey(ProgramOrderCreateDto programOrderCreateDto, Long ticketCategoryId) {
        return StrUtil.join("-",PROGRAM_ORDER_CREATE_V2, programOrderCreateDto.getProgramId(),ticketCategoryId);
    }

    private void unlockServiceLocks(List<RLock> serviceLockSuccessList) {
        for (int i = serviceLockSuccessList.size() - 1; i >= 0; i--) {
            RLock rLock = serviceLockSuccessList.get(i);
            try {
                rLock.unlock();
            } catch (Throwable t) {
                log.error("service lock unlock error",t);
            }
        }
    }

    private void unlockLocalLocks(List<ReentrantLock> localLockSuccessList) {
        for (int i = localLockSuccessList.size() - 1; i >= 0; i--) {
            ReentrantLock reentrantLock = localLockSuccessList.get(i);
            try {
                reentrantLock.unlock();
            } catch (Throwable t) {
                log.error("local lock unlock error",t);
            }
        }
    }
    
    @Override
    public Integer executeOrder() {
        return 2;
    }
    
    @Override
    public void executeInit(final ConfigurableApplicationContext context) {
        ProgramOrderContext.add(ProgramOrderVersion.V2_VERSION.getVersion(),this);
    }
}
