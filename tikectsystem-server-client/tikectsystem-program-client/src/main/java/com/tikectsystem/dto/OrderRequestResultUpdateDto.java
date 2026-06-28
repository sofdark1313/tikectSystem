package com.tikectsystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * Order request result status update parameter.
 */
@Data
@Schema(title = "OrderRequestResultUpdateDto", description = "Order request result status update parameter")
public class OrderRequestResultUpdateDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(name = "orderNumber", type = "Long", description = "Order number")
    @NotNull(message = "Order number cannot be null")
    private Long orderNumber;

    @Schema(name = "status", type = "String", description = "Result status")
    @NotBlank(message = "Result status cannot be blank")
    private String status;

    @Schema(name = "beforeStatus", type = "String", description = "Expected status before update")
    @NotBlank(message = "Expected status before update cannot be blank")
    private String beforeStatus;

    @Schema(name = "failCode", type = "String", description = "Failure code")
    private String failCode;

    @Schema(name = "failMessage", type = "String", description = "Failure message")
    private String failMessage;
}
