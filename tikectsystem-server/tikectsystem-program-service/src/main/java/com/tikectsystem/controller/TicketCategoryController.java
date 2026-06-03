package com.tikectsystem.controller;

import com.tikectsystem.common.ApiResponse;
import com.tikectsystem.dto.TicketCategoryAddDto;
import com.tikectsystem.dto.TicketCategoryDto;
import com.tikectsystem.dto.TicketCategoryListByProgramDto;
import com.tikectsystem.dto.TicketCategoryListDto;
import com.tikectsystem.service.TicketCategoryService;
import com.tikectsystem.vo.TicketCategoryDetailVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 票档 控制层
 * @author: 阿星不是程序员
 **/
@RestController
@RequestMapping("/ticket/category")
@Tag(name = "ticket-category", description = "票档")
public class TicketCategoryController {
    
    @Autowired
    private TicketCategoryService ticketCategoryService;
    
    
    @Operation(summary  = "添加")
    @PostMapping(value = "/add")
    public ApiResponse<Long> add(@Valid @RequestBody TicketCategoryAddDto ticketCategoryAddDto) {
        return ApiResponse.ok(ticketCategoryService.add(ticketCategoryAddDto));
    }
    
    @Operation(summary  = "查询详情")
    @PostMapping(value = "/detail")
    public ApiResponse<TicketCategoryDetailVo> detail(@Valid @RequestBody TicketCategoryDto ticketCategoryDto) {
        return ApiResponse.ok(ticketCategoryService.detail(ticketCategoryDto));
    }

    @Operation(summary  = "查询集合")
    @PostMapping(value = "/select/list")
    public ApiResponse<List<TicketCategoryDetailVo>> selectList(@Valid @RequestBody TicketCategoryListDto ticketCategoryDto) {
        return ApiResponse.ok(ticketCategoryService.selectList(ticketCategoryDto));
    }

    @Operation(summary  = "查询集合")
    @PostMapping(value = "/select/list/by/program")
    public ApiResponse<List<TicketCategoryDetailVo>> selectListByProgram(@Valid @RequestBody TicketCategoryListByProgramDto ticketCategoryListByProgramDto) {
        return ApiResponse.ok(ticketCategoryService.selectListByProgram(ticketCategoryListByProgramDto));
    }
}
