package com.tikectsystem.service.kafka;

import com.tikectsystem.dto.ProgramOrderCreateDto;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 下单受理请求消息。
 */
@Data
public class OrderRequestMq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 请求幂等号。
     */
    private String requestId;

    /**
     * 订单编号。
     */
    private Long orderNumber;

    /**
     * 下单参数快照。
     */
    private ProgramOrderCreateDto programOrderCreateDto;

    /**
     * 下单版本。
     */
    private Integer orderVersion;

    /**
     * 请求创建时间。
     */
    private Date createTime;
}
