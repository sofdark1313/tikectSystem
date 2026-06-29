package com.tikectsystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 异步下单请求结果状态更新参数。
 */
@Data
@Schema(title = "OrderRequestResultUpdateDto", description = "异步下单请求结果状态更新参数")
public class OrderRequestResultUpdateDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 订单编号。
     */
    @Schema(name = "orderNumber", type = "Long", description = "订单编号")
    @NotNull(message = "订单编号不能为空")
    private Long orderNumber;

    /**
     * 更新后的请求结果状态。
     */
    @Schema(name = "status", type = "String", description = "更新后的请求结果状态")
    @NotBlank(message = "请求结果状态不能为空")
    private String status;

    /**
     * 更新前必须匹配的请求结果状态，用于状态流转幂等控制。
     */
    @Schema(name = "beforeStatus", type = "String", description = "更新前必须匹配的请求结果状态")
    @NotBlank(message = "更新前请求结果状态不能为空")
    private String beforeStatus;

    /**
     * 失败编码。
     */
    @Schema(name = "failCode", type = "String", description = "失败编码")
    private String failCode;

    /**
     * 失败原因。
     */
    @Schema(name = "failMessage", type = "String", description = "失败原因")
    private String failMessage;
}
