package com.tikectsystem.controller;

import com.tikectsystem.common.ApiResponse;
import com.tikectsystem.dto.InsertMessageConsumerRecordDto;
import com.tikectsystem.dto.MessageIdDto;
import com.tikectsystem.dto.UpdateMessageConsumerRecordDto;
import com.tikectsystem.service.MessageConsumerRecordService;
import com.tikectsystem.vo.MessageConsumerRecordVo;
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
 * @description: 消息消费记录 控制层
 * @author: 阿星不是程序员
 **/
@RestController
@RequestMapping("/message/consumer/record")
@Tag(name = "/message/consumer/record", description = "消息消费记录")
public class MessageConsumerRecordController {

    @Autowired
    private MessageConsumerRecordService messageConsumerRecordService;
    
    @Operation(summary  = "查询")
    @PostMapping(value = "/getByMessageId")
    public ApiResponse<MessageConsumerRecordVo> getMessageConsumerByMessageId(@Valid @RequestBody MessageIdDto messageIdDto) {
        return ApiResponse.ok(messageConsumerRecordService.getByMessageId(messageIdDto));
    }
    
    @Operation(summary  = "添加")
    @PostMapping(value = "/insert")
    public ApiResponse<MessageConsumerRecordVo> insertMessageConsumerRecord(@Valid @RequestBody InsertMessageConsumerRecordDto insertMessageConsumerRecordDto) {
        return ApiResponse.ok(messageConsumerRecordService.insertMessageConsumerRecord(insertMessageConsumerRecordDto));
    }
    
    @Operation(summary  = "更新消息消费记录")
    @PostMapping(value = "/update")
    public ApiResponse<Boolean> updateMessageConsumerRecord(@Valid @RequestBody UpdateMessageConsumerRecordDto updateMessageConsumerRecordDto) {
        return ApiResponse.ok(messageConsumerRecordService.updateMessageConsumerRecord(updateMessageConsumerRecordDto));
    }
}
