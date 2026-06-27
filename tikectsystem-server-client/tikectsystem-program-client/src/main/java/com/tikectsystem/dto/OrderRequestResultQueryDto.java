package com.tikectsystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * Order request result query parameter.
 */
@Data
@Schema(title = "OrderRequestResultQueryDto", description = "Order request result query parameter")
public class OrderRequestResultQueryDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(name = "requestId", type = "String", description = "Order request idempotent id")
    private String requestId;

    @Schema(name = "orderNumber", type = "Long", description = "Order number")
    private Long orderNumber;
}
