package com.tikectsystem.client;

import com.tikectsystem.common.ApiResponse;
import com.tikectsystem.dto.AccountOrderCountDto;
import com.tikectsystem.dto.OrderCreateDto;
import com.tikectsystem.enums.BaseCode;
import com.tikectsystem.vo.AccountOrderCountVo;
import org.springframework.stereotype.Component;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 订单服务 feign 异常
 * @author: 阿星不是程序员
 **/
@Component
public class OrderClientFallback implements OrderClient {
    
    @Override
    public ApiResponse<String> create(final OrderCreateDto orderCreateDto) {
        return ApiResponse.error(BaseCode.SYSTEM_ERROR);
    }
    
    @Override
    public ApiResponse<AccountOrderCountVo> accountOrderCount(final AccountOrderCountDto dto) {
        return ApiResponse.error(BaseCode.SYSTEM_ERROR);
    }
}
