package com.tikectsystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Date;

/**
 * order_request Kafka 恢复扫描参数。
 */
@Data
@Schema(title = "OrderRequestRecoverDto", description = "order_request Kafka 恢复扫描参数")
public class OrderRequestRecoverDto {

    @NotNull
    @Schema(name = "faultTime", type = "Date", description = "Redis 故障检测时间")
    private Date faultTime;

    @Schema(name = "endTime", type = "Date", description = "扫描截止时间，默认当前时间")
    private Date endTime;

    @Schema(name = "safetyRollbackSeconds", type = "Integer", description = "从故障时间向前回退的安全窗口秒数")
    private Integer safetyRollbackSeconds;

    @Schema(name = "programId", type = "Long", description = "只恢复指定节目，空表示不过滤")
    private Long programId;

    @Schema(name = "onlyCommittedRequest", type = "Boolean", description = "是否只处理主消费组已提交 offset 的 request")
    private Boolean onlyCommittedRequest;
}
