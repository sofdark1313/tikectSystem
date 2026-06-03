package com.tikectsystem.client;

import com.tikectsystem.common.ApiResponse;
import com.tikectsystem.dto.AreaGetDto;
import com.tikectsystem.dto.AreaSelectDto;
import com.tikectsystem.dto.GetChannelDataByCodeDto;
import com.tikectsystem.vo.AreaVo;
import com.tikectsystem.vo.GetChannelDataVo;
import com.tikectsystem.vo.TokenDataVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

import static com.tikectsystem.constant.Constant.SPRING_INJECT_PREFIX_DISTINCTION_NAME;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 基础数据服务 feign
 * @author: 阿星不是程序员
 **/
@Component
@FeignClient(value = SPRING_INJECT_PREFIX_DISTINCTION_NAME+"-"+"base-data-service",fallback  = BaseDataClientFallback.class)
public interface BaseDataClient {
    /**
     * 根据code查询数据
     * @param dto 参数
     * @return 结果
     * */
    @PostMapping("/channel/data/getByCode")
    ApiResponse<GetChannelDataVo> getByCode(GetChannelDataByCodeDto dto);
    
    /**
     * 查询token数据
     * @return 结果
     * */
    @PostMapping(value = "/get")
    ApiResponse<TokenDataVo> get();
    
    /**
     * 根据id集合查询地区列表
     * @param dto 参数
     * @return 结果
     * */
    @PostMapping(value = "/area/selectByIdList")
    ApiResponse<List<AreaVo>> selectByIdList(AreaSelectDto dto);
    
    /**
     * 根据id查询地区
     * @param dto 参数
     * @return 结果
     * */
    @PostMapping(value = "/area/getById")
    ApiResponse<AreaVo> getById(AreaGetDto dto);
}
