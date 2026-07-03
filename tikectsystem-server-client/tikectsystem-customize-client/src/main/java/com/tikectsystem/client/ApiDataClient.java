package com.tikectsystem.client;

import com.tikectsystem.common.ApiResponse;
import com.tikectsystem.dto.InsertMessageConsumerRecordDto;
import com.tikectsystem.dto.InsertMessageProducerRecordDto;
import com.tikectsystem.dto.MessageIdDto;
import com.tikectsystem.dto.UpdateMessageConsumerRecordDto;
import com.tikectsystem.dto.UpdateMessageProducerRecordDto;
import com.tikectsystem.vo.MessageConsumerRecordVo;
import com.tikectsystem.vo.MessageProducerRecordVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;

import static com.tikectsystem.constant.Constant.SPRING_INJECT_PREFIX_DISTINCTION_NAME;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 定制服务 feign
 * @author: 阿星不是程序员
 **/
@Component
@FeignClient(value = SPRING_INJECT_PREFIX_DISTINCTION_NAME+"-"+"customize-service",fallback = ApiDataClientFallback.class)
public interface ApiDataClient {
    
    /**
     * 添加消息发送记录
     * @param insertMessageProducerRecordDto 参数
     * @return 结果
     * */
    @PostMapping(value = "/message/producer/record/insert")
    ApiResponse<MessageProducerRecordVo> insertMessageProducerRecord(InsertMessageProducerRecordDto insertMessageProducerRecordDto);
    
    /**
     * 更新消息发送记录
     * @param updateMessageProducerRecordDto 参数
     * @return 结果
     * */
    @PostMapping(value = "/message/producer/record/update")
    ApiResponse<Boolean> updateMessageProducerRecord(UpdateMessageProducerRecordDto updateMessageProducerRecordDto);
    
    /**
     * 查询消息消费记录
     * @param messageIdDto 参数
     * @return 结果
     * */
    @PostMapping(value = "/message/consumer/record/getByMessageId")
    ApiResponse<MessageConsumerRecordVo> getMessageConsumerByMessageId(MessageIdDto messageIdDto);
    
    /**
     * 添加消息消费记录
     * @param insertMessageConsumerRecordDto 参数
     * @return 结果
     * */
    @PostMapping(value = "/message/consumer/record/insert")
    ApiResponse<MessageConsumerRecordVo> insertMessageConsumerRecord(InsertMessageConsumerRecordDto insertMessageConsumerRecordDto);
    
    /**
     * 更新消息消费记录
     * @param updateMessageConsumerRecordDto 参数
     * @return 结果
     * */
    @PostMapping(value = "/message/consumer/record/update")
    ApiResponse<Boolean> updateMessageConsumerRecord(UpdateMessageConsumerRecordDto updateMessageConsumerRecordDto);
}
