package com.tikectsystem.service.strategy.impl;

import com.tikectsystem.dto.ProgramOrderCreateDto;
import com.tikectsystem.enums.ProgramOrderVersion;
import com.tikectsystem.initialize.base.AbstractApplicationCommandLineRunnerHandler;
import com.tikectsystem.service.ProgramOrderService;
import com.tikectsystem.service.strategy.ProgramOrderContext;
import com.tikectsystem.service.strategy.ProgramOrderStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 节目订单v4
 * @author: 阿星不是程序员
 **/
@Component
public class ProgramOrderV4Strategy extends AbstractApplicationCommandLineRunnerHandler implements ProgramOrderStrategy {
    
    @Autowired
    private ProgramOrderService programOrderService;

    /**
     * 创建 V4 异步订单请求。
     * 重复点击同一 requestId 时先复用已受理订单，避免座位已锁定后被前置校验误判失败。
     * @param programOrderCreateDto 下单参数
     * @return 订单编号
     */
    @Override
    public String createOrder(ProgramOrderCreateDto programOrderCreateDto) {
        String acceptedOrderNumber = programOrderService.reuseAcceptedOrderRequestIfPresent(programOrderCreateDto);
        if (Objects.nonNull(acceptedOrderNumber)) {
            return acceptedOrderNumber;
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
