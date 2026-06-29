package com.tikectsystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 异步下单请求结果查询参数。
 */
@Data
@Schema(title = "OrderRequestResultQueryDto", description = "异步下单请求结果查询参数")
public class OrderRequestResultQueryDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 下单请求幂等号。
     */
    @Schema(name = "requestId", type = "String", description = "下单请求幂等号")
    private String requestId;

    /**
     * 订单编号。
     */
    @Schema(name = "orderNumber", type = "Long", description = "订单编号")
    private Long orderNumber;
}
