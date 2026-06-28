package com.tikectsystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 添加消息发送记录 dto
 * @author: 阿星不是程序员
 **/
@Data
@Schema(title="InsertMessageProducerRecordDto", description ="添加消息发送记录")
public class InsertMessageProducerRecordDto {

    
    @Schema(name ="messageType", type ="Integer", description ="消息类型", requiredMode= RequiredMode.REQUIRED)
    @NotNull
    private Integer messageType;
    
    /**
     * 消息的链路id
     */
    @Schema(name ="messageTraceId", type ="Long", description ="消息的链路id", requiredMode= RequiredMode.REQUIRED)
    @NotNull
    private Long messageTraceId;
    
    /**
     * 消息业务id
     */
    @Schema(name ="messageBusinessesId", type ="Long", description ="消息业务id", requiredMode= RequiredMode.REQUIRED)
    @NotNull
    private Long messageBusinessesId;
    
    /**
     * 消息id
     */
    @Schema(name ="messageId", type ="Long", description ="消息id", requiredMode= RequiredMode.REQUIRED)
    @NotNull
    private Long messageId;

    /**
     * Kafka 原始消息 key，用于补偿重发时保持分区键一致
     */
    @Schema(name ="messageKey", type ="String", description ="Kafka原始消息key")
    private String messageKey;
    
    /**
     * 消息topic
     */
    @Schema(name ="messageTopic", type ="String", description ="消息topic", requiredMode= RequiredMode.REQUIRED)
    @NotBlank
    private String messageTopic;
    
    /**
     * 消息内容
     */
    @Schema(name ="messageContent", type ="String", description ="消息内容", requiredMode= RequiredMode.REQUIRED)
    @NotNull
    private String messageContent;
}
