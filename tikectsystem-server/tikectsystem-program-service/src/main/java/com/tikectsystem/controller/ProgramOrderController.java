package com.tikectsystem.controller;

import com.tikectsystem.common.ApiResponse;
import com.tikectsystem.dto.ProgramOrderCreateDto;
import com.tikectsystem.enums.ProgramOrderVersion;
import com.tikectsystem.service.strategy.ProgramOrderContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
    
    /**
     * 统一正式下单入口，只保留 V4 异步闭环链路。
     */
    @Operation(summary  = "购票V4")
    @PostMapping(value = "/create/v4")
    public ApiResponse<String> createV4(@Valid @RequestBody ProgramOrderCreateDto programOrderCreateDto) {
        String orderNumber = ProgramOrderContext.get(ProgramOrderVersion.V4_VERSION.getVersion())
                .createOrder(programOrderCreateDto);
        return ApiResponse.ok(orderNumber);
    }
}
