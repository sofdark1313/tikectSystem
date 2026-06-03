package com.tikectsystem.service.strategy.impl;

import com.tikectsystem.core.RepeatExecuteLimitConstants;
import com.tikectsystem.dto.ProgramOrderCreateDto;
import com.tikectsystem.enums.CompositeCheckType;
import com.tikectsystem.enums.ProgramOrderVersion;
import com.tikectsystem.initialize.base.AbstractApplicationCommandLineRunnerHandler;
import com.tikectsystem.initialize.impl.composite.CompositeContainer;
import com.tikectsystem.repeatexecutelimit.annotion.RepeatExecuteLimit;
import com.tikectsystem.service.ProgramOrderService;
import com.tikectsystem.service.strategy.BaseProgramOrder;
import com.tikectsystem.service.strategy.ProgramOrderContext;
import com.tikectsystem.service.strategy.ProgramOrderStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import static com.tikectsystem.core.DistributedLockConstants.PROGRAM_ORDER_CREATE_V4;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 节目订单v4
 * @author: 阿星不是程序员
 **/
@Slf4j
@Component
public class ProgramOrderV4Strategy extends AbstractApplicationCommandLineRunnerHandler implements ProgramOrderStrategy {
    
    @Autowired
    private ProgramOrderService programOrderService;
    
    @Autowired
    private BaseProgramOrder baseProgramOrder;
    
    @Autowired
    private CompositeContainer compositeContainer;
    
    /*@RepeatExecuteLimit(
            name = RepeatExecuteLimitConstants.CREATE_PROGRAM_ORDER,
            keys = {"#programOrderCreateDto.userId","#programOrderCreateDto.programId"})
    */
    @Override
    public String createOrder(ProgramOrderCreateDto programOrderCreateDto) {
        compositeContainer.execute(CompositeCheckType.PROGRAM_ORDER_CREATE_CHECK.getValue(),programOrderCreateDto);
        return baseProgramOrder.localLockCreateOrder(PROGRAM_ORDER_CREATE_V4,programOrderCreateDto,
                () -> programOrderService.createNewAsync(programOrderCreateDto,ProgramOrderVersion.V4_VERSION.getValue()));
    }
    
    @Override
    public Integer executeOrder() {
        return 4;
    }
    
    @Override
    public void executeInit(final ConfigurableApplicationContext context) {
        ProgramOrderContext.add(ProgramOrderVersion.V4_VERSION.getVersion(),this);
    }
}
