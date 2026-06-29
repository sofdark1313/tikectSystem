package com.tikectsystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 异步下单请求结果过期参数。
 */
@Data
@Schema(title = "OrderRequestResultExpireDto", description = "异步下单请求结果过期参数")
public class OrderRequestResultExpireDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建时间早于该时间的 PROCESSING 请求会被置为 EXPIRED。
     */
    @NotNull(message = "过期判断时间不能为空")
    @Schema(name = "beforeTime", type = "Date", description = "过期判断时间")
    private Date beforeTime;

    /**
     * 单次最多处理数量。
     */
    @Min(value = 1, message = "处理数量必须大于0")
    @Schema(name = "limit", type = "Integer", description = "单次最多处理数量")
    private Integer limit = 100;
}
