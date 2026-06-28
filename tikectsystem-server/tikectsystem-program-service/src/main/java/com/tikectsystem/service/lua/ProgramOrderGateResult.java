package com.tikectsystem.service.lua;

import lombok.Data;

/**
 * 下单入口准入 Lua 执行结果。
 */
@Data
public class ProgramOrderGateResult {

    /**
     * 结果编码，0 为成功。
     */
    private Integer code;

    /**
     * 已存在请求对应的订单编号。
     */
    private String orderNumber;
}
