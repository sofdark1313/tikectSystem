package com.tikectsystem.service.strategy.impl;

import com.tikectsystem.dto.ProgramOrderCreateDto;
import com.tikectsystem.enums.BaseCode;
import com.tikectsystem.enums.CompositeCheckType;
import com.tikectsystem.enums.ProgramOrderVersion;
import com.tikectsystem.exception.TikectsystemFrameException;
import com.tikectsystem.initialize.base.AbstractApplicationCommandLineRunnerHandler;
import com.tikectsystem.initialize.impl.composite.CompositeContainer;
import com.tikectsystem.service.ProgramOrderService;
import com.tikectsystem.service.strategy.ProgramOrderContext;
import com.tikectsystem.service.strategy.ProgramOrderStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

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
    private CompositeContainer compositeContainer;
    
    @Override
    public String createOrder(ProgramOrderCreateDto programOrderCreateDto) {
        try {
            compositeContainer.execute(CompositeCheckType.PROGRAM_ORDER_CREATE_CHECK.getValue(),programOrderCreateDto);
        } catch (TikectsystemFrameException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("program order pre check failed, programId : {}", programOrderCreateDto.getProgramId(), e);
            throw new TikectsystemFrameException(BaseCode.PROGRAM_ORDER_CIRCUIT_OPEN);
        }
        return programOrderService.acceptOrderRequest(programOrderCreateDto, ProgramOrderVersion.V4_VERSION.getValue());
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
