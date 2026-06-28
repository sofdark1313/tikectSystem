package com.tikectsystem.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 节目下单 Redis 熔断状态。
 */
@Data
@Schema(title = "ProgramOrderCircuitStateVo", description = "节目下单 Redis 熔断状态")
public class ProgramOrderCircuitStateVo {

    private Long programId;

    private Long ticketCategoryId;

    private String state;

    private String reason;

    private Integer limitedQps;

    private Integer halfOpenMaxInFlight;

    private Integer halfOpenSuccessThreshold;

    private Integer halfOpenSuccessCount;

    private Integer halfOpenInFlight;

    private Date updateTime;
}
