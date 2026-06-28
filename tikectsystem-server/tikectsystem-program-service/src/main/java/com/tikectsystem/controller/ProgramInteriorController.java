package com.tikectsystem.controller;

import com.tikectsystem.common.ApiResponse;
import com.tikectsystem.dto.OrderRequestRecoverDto;
import com.tikectsystem.dto.OrderRequestResultUpdateDto;
import com.tikectsystem.dto.ProgramOperateDataDto;
import com.tikectsystem.dto.ReduceRemainNumberDto;
import com.tikectsystem.service.OrderRequestResultService;
import com.tikectsystem.service.ProgramService;
import com.tikectsystem.service.kafka.OrderRequestRecoveryService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Program internal RPC controller.
 */
@RestController
@RequestMapping("/program/interior")
public class ProgramInteriorController {

    @Autowired
    private ProgramService programService;

    @Autowired
    private OrderRequestResultService orderRequestResultService;

    @Autowired
    private OrderRequestRecoveryService orderRequestRecoveryService;

    @Operation(summary  = "扣减库存相关操作")
    @PostMapping(value = "/reduce/remain/number")
    public ApiResponse<Boolean> operateSeatLockAndTicketCategoryRemainNumber(@Valid @RequestBody ReduceRemainNumberDto reduceRemainNumberDto) {
        return ApiResponse.ok(programService.operateSeatLockAndTicketCategoryRemainNumber(reduceRemainNumberDto));
    }

    @Operation(summary  = "订单支付成功或取消后操作节目数据")
    @PostMapping(value = "/operate/program/data")
    public ApiResponse<Boolean> operateProgramData(@Valid @RequestBody ProgramOperateDataDto programOperateDataDto) {
        return ApiResponse.ok(programService.operateProgramData(programOperateDataDto));
    }

    @Operation(summary  = "回写异步下单请求结果")
    @PostMapping(value = "/order/request/result/update")
    public ApiResponse<Boolean> updateOrderRequestResult(@Valid @RequestBody OrderRequestResultUpdateDto orderRequestResultUpdateDto) {
        return ApiResponse.ok(orderRequestResultService.updateStatus(orderRequestResultUpdateDto));
    }

    @Operation(summary  = "Redis 故障恢复时回扫 order_request")
    @PostMapping(value = "/order/request/recover")
    public ApiResponse<Integer> recoverOrderRequest(@Valid @RequestBody OrderRequestRecoverDto orderRequestRecoverDto) {
        return ApiResponse.ok(orderRequestRecoveryService.recover(orderRequestRecoverDto));
    }
}
