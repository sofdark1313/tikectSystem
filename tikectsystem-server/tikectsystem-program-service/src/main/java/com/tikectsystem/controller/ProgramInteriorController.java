package com.tikectsystem.controller;

import com.tikectsystem.common.ApiResponse;
import com.tikectsystem.dto.OrderRequestRecoverDto;
import com.tikectsystem.dto.OrderRequestResultExpireDto;
import com.tikectsystem.dto.OrderRequestResultQueryDto;
import com.tikectsystem.dto.OrderRequestResultUpdateDto;
import com.tikectsystem.dto.ProgramOrderCircuitOperateDto;
import com.tikectsystem.dto.ProgramOrderCircuitQueryDto;
import com.tikectsystem.dto.ProgramOperateDataDto;
import com.tikectsystem.dto.ReduceRemainNumberDto;
import com.tikectsystem.properties.ApiVerify;
import com.tikectsystem.service.OrderRequestResultService;
import com.tikectsystem.service.ProgramOrderCircuitBreakerService;
import com.tikectsystem.service.ProgramService;
import com.tikectsystem.service.kafka.OrderRequestRecoveryService;
import com.tikectsystem.vo.OrderRequestResultVo;
import com.tikectsystem.vo.ProgramOrderCircuitStateVo;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Program internal RPC controller.
 */
@RestController
@RequestMapping("/program/interior")
public class ProgramInteriorController {

    @Autowired
    private ProgramService programService;

    @Autowired
    private OrderRequestRecoveryService orderRequestRecoveryService;

    @Autowired
    private ProgramOrderCircuitBreakerService programOrderCircuitBreakerService;

    @Autowired
    private OrderRequestResultService orderRequestResultService;

    @Autowired
    private ApiVerify apiVerify;

    @Operation(summary  = "扣减库存相关操作")
    @PostMapping(value = "/reduce/remain/number")
    public ApiResponse<Boolean> operateSeatLockAndTicketCategoryRemainNumber(@Valid @RequestBody ReduceRemainNumberDto reduceRemainNumberDto) {
        apiVerify.verifyApi();
        return ApiResponse.ok(programService.operateSeatLockAndTicketCategoryRemainNumber(reduceRemainNumberDto));
    }

    @Operation(summary  = "订单支付成功或取消后操作节目数据")
    @PostMapping(value = "/operate/program/data")
    public ApiResponse<Boolean> operateProgramData(@Valid @RequestBody ProgramOperateDataDto programOperateDataDto) {
        apiVerify.verifyApi();
        return ApiResponse.ok(programService.operateProgramData(programOperateDataDto));
    }

    @Operation(summary  = "Redis 故障恢复时回扫 order_request")
    @PostMapping(value = "/order/request/recover")
    public ApiResponse<Integer> recoverOrderRequest(@Valid @RequestBody OrderRequestRecoverDto orderRequestRecoverDto) {
        apiVerify.verifyApi();
        return ApiResponse.ok(orderRequestRecoveryService.recover(orderRequestRecoverDto));
    }

    @Operation(summary  = "更新异步下单请求结果")
    @PostMapping(value = "/order/request/result/update")
    public ApiResponse<Boolean> updateOrderRequestResult(
            @Valid @RequestBody OrderRequestResultUpdateDto orderRequestResultUpdateDto) {
        apiVerify.verifyApi();
        return ApiResponse.ok(orderRequestResultService.updateStatus(orderRequestResultUpdateDto));
    }

    @Operation(summary  = "查询异步下单请求结果")
    @PostMapping(value = "/order/request/result/get")
    public ApiResponse<OrderRequestResultVo> getOrderRequestResult(
            @Valid @RequestBody OrderRequestResultQueryDto orderRequestResultQueryDto) {
        apiVerify.verifyApi();
        return ApiResponse.ok(orderRequestResultService.get(orderRequestResultQueryDto));
    }

    @Operation(summary  = "过期卡住的异步下单请求")
    @PostMapping(value = "/order/request/result/expire")
    public ApiResponse<Integer> expireOrderRequestResult(
            @Valid @RequestBody OrderRequestResultExpireDto orderRequestResultExpireDto) {
        apiVerify.verifyApi();
        int limit = orderRequestResultExpireDto.getLimit() == null ? 100 : orderRequestResultExpireDto.getLimit();
        return ApiResponse.ok(orderRequestResultService.expireStuckProcessing(
                orderRequestResultExpireDto.getBeforeTime(), limit));
    }

    @Operation(summary  = "更新节目下单 Redis 熔断状态")
    @PostMapping(value = "/order/circuit/update")
    public ApiResponse<ProgramOrderCircuitStateVo> updateOrderCircuit(
            @Valid @RequestBody ProgramOrderCircuitOperateDto programOrderCircuitOperateDto) {
        apiVerify.verifyApi();
        return ApiResponse.ok(programOrderCircuitBreakerService.updateState(programOrderCircuitOperateDto));
    }

    @Operation(summary  = "查询节目下单 Redis 熔断状态")
    @PostMapping(value = "/order/circuit/get")
    public ApiResponse<ProgramOrderCircuitStateVo> getOrderCircuit(
            @Valid @RequestBody ProgramOrderCircuitQueryDto programOrderCircuitQueryDto) {
        apiVerify.verifyApi();
        return ApiResponse.ok(programOrderCircuitBreakerService.getState(programOrderCircuitQueryDto));
    }

    @Operation(summary  = "查询全部节目下单 Redis 熔断状态")
    @PostMapping(value = "/order/circuit/list")
    public ApiResponse<List<ProgramOrderCircuitStateVo>> listOrderCircuit() {
        apiVerify.verifyApi();
        return ApiResponse.ok(programOrderCircuitBreakerService.listState());
    }
}
