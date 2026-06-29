package com.tikectsystem.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 异步下单请求结果视图。
 */
@Data
@Schema(title = "OrderRequestResultVo", description = "异步下单请求结果视图")
public class OrderRequestResultVo implements Serializable {

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

    /**
     * 节目编号。
     */
    @Schema(name = "programId", type = "Long", description = "节目编号")
    private Long programId;

    /**
     * 用户编号。
     */
    @Schema(name = "userId", type = "Long", description = "用户编号")
    private Long userId;

    /**
     * 请求结果状态。
     */
    @Schema(name = "status", type = "String", description = "PROCESSING/RESERVED/ORDER_CREATED/FAILED/CANCELLED/EXPIRED")
    private String status;

    /**
     * Redis 锁座快照 JSON。
     */
    @Schema(name = "reservationJson", type = "String", description = "Redis 锁座快照 JSON")
    private String reservationJson;

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

    /**
     * 锁座过期时间。
     */
    @Schema(name = "expireTime", type = "Date", description = "锁座过期时间")
    private Date expireTime;
}
