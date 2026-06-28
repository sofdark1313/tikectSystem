package com.tikectsystem.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * Order request result view.
 */
@Data
@Schema(title = "OrderRequestResultVo", description = "Order request result view")
public class OrderRequestResultVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(name = "requestId", type = "String", description = "Order request idempotent id")
    private String requestId;

    @Schema(name = "orderNumber", type = "Long", description = "Order number")
    private Long orderNumber;

    @Schema(name = "programId", type = "Long", description = "Program id")
    private Long programId;

    @Schema(name = "userId", type = "Long", description = "User id")
    private Long userId;

    @Schema(name = "status", type = "String", description = "PROCESSING/RESERVED/ORDER_CREATED/FAILED/CANCELLED/EXPIRED")
    private String status;

    @Schema(name = "reservationJson", type = "String", description = "Redis reservation snapshot")
    private String reservationJson;

    @Schema(name = "failCode", type = "String", description = "Failure code")
    private String failCode;

    @Schema(name = "failMessage", type = "String", description = "Failure message")
    private String failMessage;

    @Schema(name = "expireTime", type = "Date", description = "Reservation expire time")
    private Date expireTime;
}
