package com.tikectsystem.client;

import com.tikectsystem.common.ApiResponse;
import com.tikectsystem.dto.InsertMessageConsumerRecordDto;
import com.tikectsystem.dto.InsertMessageProducerRecordDto;
import com.tikectsystem.dto.MessageIdDto;
import com.tikectsystem.dto.UpdateMessageConsumerRecordDto;
import com.tikectsystem.dto.UpdateMessageProducerRecordDto;
import com.tikectsystem.enums.BaseCode;
import com.tikectsystem.vo.MessageConsumerRecordVo;
import com.tikectsystem.vo.MessageProducerRecordVo;
import org.springframework.stereotype.Component;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 定制服务 feign 异常
 * @author: 阿星不是程序员
 **/
@Component
public class ApiDataClientFallback implements ApiDataClient {
    
    @Override
    public ApiResponse<MessageProducerRecordVo> insertMessageProducerRecord(final InsertMessageProducerRecordDto insertMessageProducerRecordDto) {
        return ApiResponse.error(BaseCode.SYSTEM_ERROR);
    }
    
    @Override
    public ApiResponse<Boolean> updateMessageProducerRecord(final UpdateMessageProducerRecordDto updateMessageProducerRecordDto) {
        return ApiResponse.error(BaseCode.SYSTEM_ERROR);
    }
    
    @Override
    public ApiResponse<MessageConsumerRecordVo> getMessageConsumerByMessageId(final MessageIdDto messageIdDto) {
        return ApiResponse.error(BaseCode.SYSTEM_ERROR);
    }
    
    @Override
    public ApiResponse<MessageConsumerRecordVo> insertMessageConsumerRecord(final InsertMessageConsumerRecordDto insertMessageConsumerRecordDto) {
        return ApiResponse.error(BaseCode.SYSTEM_ERROR);
    }
    
    @Override
    public ApiResponse<Boolean> updateMessageConsumerRecord(final UpdateMessageConsumerRecordDto updateMessageConsumerRecordDto) {
        return ApiResponse.error(BaseCode.SYSTEM_ERROR);
    }
}
