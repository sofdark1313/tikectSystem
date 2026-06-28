package com.tikectsystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 节目下单 Redis 熔断状态查询参数。
 */
@Data
@Schema(title = "ProgramOrderCircuitQueryDto", description = "节目下单 Redis 熔断状态查询参数")
public class ProgramOrderCircuitQueryDto {

    @NotNull
    @Schema(name = "programId", type = "Long", description = "节目编号")
    private Long programId;

    @Schema(name = "ticketCategoryId", type = "Long", description = "票档编号，空表示节目维度")
    private Long ticketCategoryId;
}
