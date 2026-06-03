package com.tikectsystem.service.composite;

import com.tikectsystem.dto.ProgramRecommendListDto;
import com.tikectsystem.enums.BaseCode;
import com.tikectsystem.enums.CompositeCheckType;
import com.tikectsystem.exception.TikectsystemFrameException;
import com.tikectsystem.initialize.impl.composite.AbstractComposite;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 节目推荐参数验证
 * @author: 阿星不是程序员
 **/
@Component
public class ProgramRecommendCheckHandler extends AbstractComposite<ProgramRecommendListDto> {
    
    @Override
    protected void execute(final ProgramRecommendListDto param) {
        if (Objects.isNull(param.getAreaId()) && 
                Objects.isNull(param.getParentProgramCategoryId()) &&
                Objects.isNull(param.getProgramId())) {
            throw new TikectsystemFrameException(BaseCode.PARAMETERS_CANNOT_BE_EMPTY);
        }
    }
    
    @Override
    public String type() {
        return CompositeCheckType.PROGRAM_RECOMMEND_CHECK.getValue();
    }
    
    @Override
    public Integer executeParentOrder() {
        return 0;
    }
    
    @Override
    public Integer executeTier() {
        return 1;
    }
    
    @Override
    public Integer executeOrder() {
        return 1;
    }
}
