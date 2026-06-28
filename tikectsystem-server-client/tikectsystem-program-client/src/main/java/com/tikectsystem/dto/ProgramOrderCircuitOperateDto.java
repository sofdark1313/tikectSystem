package com.tikectsystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 节目下单 Redis 熔断状态操作参数。
 */
@Data
@Schema(title = "ProgramOrderCircuitOperateDto", description = "节目下单 Redis 熔断状态操作参数")
public class ProgramOrderCircuitOperateDto {

    @NotNull
    @Schema(name = "programId", type = "Long", description = "节目编号")
    private Long programId;

    @Schema(name = "ticketCategoryId", type = "Long", description = "票档编号，空表示节目维度")
    private Long ticketCategoryId;

    @NotBlank
    @Schema(name = "state", type = "String", description = "NORMAL/LIMITED/FROZEN/RECOVERING/HALF_OPEN")
    private String state;

    @Schema(name = "reason", type = "String", description = "状态变更原因")
    private String reason;

    @Schema(name = "limitedQps", type = "Integer", description = "LIMITED 状态每秒允许请求数")
    private Integer limitedQps;

    @Schema(name = "halfOpenMaxInFlight", type = "Integer", description = "HALF_OPEN 同时允许探测请求数")
    private Integer halfOpenMaxInFlight;

    @Schema(name = "halfOpenSuccessThreshold", type = "Integer", description = "HALF_OPEN 连续成功多少次后恢复 NORMAL")
    private Integer halfOpenSuccessThreshold;
}
