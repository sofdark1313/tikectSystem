package com.tikectsystem.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * V4 异步下单受理结果。
 */
@Data
@Schema(title = "ProgramOrderCreateVo", description = "V4 异步下单受理结果")
public class ProgramOrderCreateVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 已受理的订单编号。
     */
    @Schema(name = "orderNumber", type = "String", description = "订单编号")
    private String orderNumber;

    /**
     * 服务端最终采用的下单请求幂等号。
     */
    @Schema(name = "requestId", type = "String", description = "下单请求幂等号")
    private String requestId;
}
