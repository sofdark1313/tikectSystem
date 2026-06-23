package com.tikectsystem.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.tikectsystem.dto.ProgramListDto;
import com.tikectsystem.dto.ProgramPageListDto;
import com.tikectsystem.dto.ProgramRecommendListDto;
import com.tikectsystem.entity.Program;
import com.tikectsystem.entity.ProgramJoinShowTime;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 节目 mapper
 * @author: 阿星不是程序员
 **/
public interface ProgramMapper extends BaseMapper<Program> {

    /**
     * 主页查询
     * @param programListDto 参数
     * @return 结果
     * */
    List<Program> selectHomeList(@Param("programListDto")ProgramListDto programListDto);

    /**
     * 分页查询
     * @param page 分页对象
     * @param programPageListDto 参数
     * @return 结果
     * */
    IPage<ProgramJoinShowTime> selectPage(IPage<ProgramJoinShowTime> page,
                                          @Param("programPageListDto")ProgramPageListDto programPageListDto);

    /**
     * 推荐查询
     * @param programRecommendListDto 推荐参数
     * @param limit 查询数量
     * @return 结果
     * */
    List<ProgramJoinShowTime> selectRecommendList(@Param("programRecommendListDto") ProgramRecommendListDto programRecommendListDto,
                                                  @Param("limit") Integer limit);
}
