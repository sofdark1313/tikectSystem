package com.tikectsystem.client;

import com.tikectsystem.common.ApiResponse;
import com.tikectsystem.dto.NotifyDto;
import com.tikectsystem.dto.PayDto;
import com.tikectsystem.dto.RefundDto;
import com.tikectsystem.dto.TradeCheckDto;
import com.tikectsystem.vo.NotifyVo;
import com.tikectsystem.vo.TradeCheckVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;

import static com.tikectsystem.constant.Constant.SPRING_INJECT_PREFIX_DISTINCTION_NAME;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 支付服务 feign
 * @author: 阿星不是程序员
 **/
@Component
@FeignClient(value = SPRING_INJECT_PREFIX_DISTINCTION_NAME+"-"+"pay-service",fallback = PayClientFallback.class)
public interface PayClient {
    /**
     * 支付
     * @param dto 参数
     * @return 结果
     * */
    @PostMapping(value = "/pay/common/pay")
    ApiResponse<String> commonPay(PayDto dto);
    /**
     * 回调
     * @param dto 参数
     * @return 结果
     * */
    @PostMapping(value = "/pay/notify")
    ApiResponse<NotifyVo> notify(NotifyDto dto);
    /**
     * 查询支付状态
     * @param dto 参数
     * @return 结果
     * */
    @PostMapping(value = "/pay/trade/check")
    ApiResponse<TradeCheckVo> tradeCheck(TradeCheckDto dto);
    /**
     * 退款
     * @param dto 参数
     * @return 结果
     * */
    @PostMapping(value = "/pay/refund")
    ApiResponse<String> refund(RefundDto dto);
}
