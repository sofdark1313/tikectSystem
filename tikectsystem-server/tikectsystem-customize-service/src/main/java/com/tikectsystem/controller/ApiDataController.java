package com.tikectsystem.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tikectsystem.common.ApiResponse;
import com.tikectsystem.dto.ApiDataDto;
import com.tikectsystem.properties.ApiVerify;
import com.tikectsystem.service.ApiDataService;
import com.tikectsystem.vo.ApiDataVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: api调用记录 控制层
 * @author: 阿星不是程序员
 **/
@RestController
@RequestMapping("/apiData")
@Tag(name = "apiData", description = "api调用记录")
public class ApiDataController {
    
    @Autowired
    private ApiDataService apiDataService;

    @Autowired
    private ApiVerify apiVerify;
    
    @Operation(summary  = "分页查询api调用记录")
    @RequestMapping(value = "/pageList",method = RequestMethod.POST)
    public ApiResponse<Page<ApiDataVo>> pageList(@Valid @RequestBody ApiDataDto dto) {
        apiVerify.verifyApi();
        return ApiResponse.ok(apiDataService.pageList(dto));
    }
}
