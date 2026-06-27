package com.tikectsystem.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tikectsystem.data.BaseTableData;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 异步下单请求结果表实体。
 */
@Data
@TableName("order_request_result")
public class OrderRequestResult extends BaseTableData implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键。
     */
    private Long id;

    /**
     * 下单请求幂等号。
     */
    private String requestId;

    /**
     * 订单编号。
     */
    private Long orderNumber;

    /**
     * 节目编号。
     */
    private Long programId;

    /**
     * 用户编号。
     */
    private Long userId;

    /**
     * 下单请求状态。
     */
    private String resultStatus;

    /**
     * Redis 锁座快照 JSON。
     */
    private String reservationJson;

    /**
     * 失败编码。
     */
    private String failCode;

    /**
     * 失败原因。
     */
    private String failMessage;

    /**
     * 锁座过期时间。
     */
    private Date expireTime;
}
