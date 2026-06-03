package com.tikectsystem.client;

import com.tikectsystem.common.ApiResponse;
import com.tikectsystem.dto.AccountOrderCountDto;
import com.tikectsystem.dto.OrderCreateDto;
import com.tikectsystem.vo.AccountOrderCountVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;

import static com.tikectsystem.constant.Constant.SPRING_INJECT_PREFIX_DISTINCTION_NAME;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 订单服务 feign
 * @author: 阿星不是程序员
 **/
@Component
@FeignClient(value = SPRING_INJECT_PREFIX_DISTINCTION_NAME+"-"+"order-service",fallback = OrderClientFallback.class)
public interface OrderClient {
    
    /**
     * 创建订单
     * @param dto 参数
     * @return 结果
     * */
    @PostMapping("/order/create")
    ApiResponse<String> create(OrderCreateDto dto);
    
    /**
     * 账户下某个节目的订单数量
     * @param dto 参数
     * @return 结果
     * */
    @PostMapping("/order/account/order/count")
    ApiResponse<AccountOrderCountVo> accountOrderCount(AccountOrderCountDto dto);
}
