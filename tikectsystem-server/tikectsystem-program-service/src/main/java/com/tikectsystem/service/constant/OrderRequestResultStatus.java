package com.tikectsystem.service.constant;

/**
 * 异步下单请求结果状态。
 */
public final class OrderRequestResultStatus {

    private OrderRequestResultStatus() {
    }

    public static final String PROCESSING = "PROCESSING";

    public static final String RESERVED = "RESERVED";

    public static final String ORDER_CREATED = "ORDER_CREATED";

    public static final String FAILED = "FAILED";

    public static final String CANCELLED = "CANCELLED";

    public static final String EXPIRED = "EXPIRED";
}
