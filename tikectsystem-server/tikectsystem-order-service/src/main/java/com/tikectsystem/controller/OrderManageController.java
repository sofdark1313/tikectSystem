package com.tikectsystem.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.tikectsystem.common.ApiResponse;
import com.tikectsystem.dto.OrderPageManageDto;
import com.tikectsystem.dto.RecordManageDto;
import com.tikectsystem.service.OrderManageService;
import com.tikectsystem.vo.DiscardOrderManageVo;
import com.tikectsystem.vo.OrderManageVo;
import com.tikectsystem.vo.RecordOrderManageVo;
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
 * @description: 订单后台管理 控制层
 * @author: 阿星不是程序员
 **/
@RestController
@RequestMapping("/order/manage")
@Tag(name = "order/manage", description = "订单")
public class OrderManageController {
    
    @Autowired
    private OrderManageService orderManageService;

    

    @Operation(summary  = "操作记录分页列表")
    @PostMapping(value = "/record/page")
    public ApiResponse<IPage<RecordOrderManageVo>> recordPage(@Valid @RequestBody RecordManageDto recordManageDto) {
        return ApiResponse.ok(orderManageService.recordPage(recordManageDto));
    }
    
    @Operation(summary  = "查看订单分页列表")
    @PostMapping(value = "/order/page")
    public ApiResponse<IPage<OrderManageVo>> orderPage(@Valid @RequestBody OrderPageManageDto orderPageManageDto) {
        return ApiResponse.ok(orderManageService.orderPage(orderPageManageDto));
    }
    
    @Operation(summary  = "查看废弃订单分页列表")
    @PostMapping(value = "/discard/order/page")
    public ApiResponse<IPage<DiscardOrderManageVo>> discardOrderPage(@Valid @RequestBody OrderPageManageDto orderPageManageDto) {
        return ApiResponse.ok(orderManageService.discardOrderPage(orderPageManageDto));
    }
}
