package com.tikectsystem.controller;

import com.tikectsystem.common.ApiResponse;
import com.tikectsystem.dto.OrderRequestResultQueryDto;
import com.tikectsystem.dto.ProgramOrderCreateDto;
import com.tikectsystem.enums.ProgramOrderVersion;
import com.tikectsystem.service.OrderRequestResultService;
import com.tikectsystem.service.strategy.ProgramOrderContext;
import com.tikectsystem.vo.OrderRequestResultVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 节目订单 控制层
 * @author: 阿星不是程序员
 **/
@RestController
@RequestMapping("/program/order")
@Tag(name = "program-order", description = "节目订单")
public class ProgramOrderController {

    @Autowired
    private OrderRequestResultService orderRequestResultService;
    
    @Operation(summary  = "购票V1")
    @PostMapping(value = "/create/v1")
    public ApiResponse<String> createV1(@Valid @RequestBody ProgramOrderCreateDto programOrderCreateDto) {
        return ApiResponse.ok(ProgramOrderContext.get(ProgramOrderVersion.V1_VERSION.getVersion())
                .createOrder(programOrderCreateDto));
    }
    
    @Operation(summary  = "购票V2")
    @PostMapping(value = "/create/v2")
    public ApiResponse<String> createV2(@Valid @RequestBody ProgramOrderCreateDto programOrderCreateDto) {
        return ApiResponse.ok(ProgramOrderContext.get(ProgramOrderVersion.V2_VERSION.getVersion())
                .createOrder(programOrderCreateDto));
    }
    
    @Operation(summary  = "购票V21")
    @PostMapping(value = "/create/v21")
    public ApiResponse<String> createV21(@Valid @RequestBody ProgramOrderCreateDto programOrderCreateDto) {
        return ApiResponse.ok(ProgramOrderContext.get(ProgramOrderVersion.V21_VERSION.getVersion())
                .createOrder(programOrderCreateDto));
    }
    
    @Operation(summary  = "购票V3")
    @PostMapping(value = "/create/v3")
    public ApiResponse<String> createV3(@Valid @RequestBody ProgramOrderCreateDto programOrderCreateDto) {
        return ApiResponse.ok(ProgramOrderContext.get(ProgramOrderVersion.V3_VERSION.getVersion())
                .createOrder(programOrderCreateDto));
    }
    
    @Operation(summary  = "购票V31")
    @PostMapping(value = "/create/v31")
    public ApiResponse<String> createV31(@Valid @RequestBody ProgramOrderCreateDto programOrderCreateDto) {
        return ApiResponse.ok(ProgramOrderContext.get(ProgramOrderVersion.V31_VERSION.getVersion())
                .createOrder(programOrderCreateDto));
    }
    
    @Operation(summary  = "购票V4")
    @PostMapping(value = "/create/v4")
    public ApiResponse<String> createV4(@Valid @RequestBody ProgramOrderCreateDto programOrderCreateDto) {
        return ApiResponse.ok(ProgramOrderContext.get(ProgramOrderVersion.V4_VERSION.getVersion())
                .createOrder(programOrderCreateDto));
    }
    
    @Operation(summary  = "购票V4")
    @PostMapping(value = "/create/v41")
    public ApiResponse<String> createV41(@Valid @RequestBody ProgramOrderCreateDto programOrderCreateDto) {
        return ApiResponse.ok(ProgramOrderContext.get(ProgramOrderVersion.V41_VERSION.getVersion())
                .createOrder(programOrderCreateDto));
    }

    @Operation(summary  = "查询异步下单结果")
    @PostMapping(value = "/result")
    public ApiResponse<OrderRequestResultVo> result(@RequestBody OrderRequestResultQueryDto orderRequestResultQueryDto) {
        return ApiResponse.ok(orderRequestResultService.get(orderRequestResultQueryDto));
    }
}
