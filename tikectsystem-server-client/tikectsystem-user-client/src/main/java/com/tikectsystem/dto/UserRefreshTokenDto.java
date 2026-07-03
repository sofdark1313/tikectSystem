package com.tikectsystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户刷新访问令牌入参。
 */
@Data
@Schema(title = "UserRefreshTokenDto", description = "用户刷新访问令牌")
public class UserRefreshTokenDto {

    @Schema(name = "code", type = "String", description = "渠道code 0001:pc网站", requiredMode = RequiredMode.REQUIRED)
    @NotBlank
    private String code;

    @Schema(name = "refreshToken", type = "String", description = "刷新令牌", requiredMode = RequiredMode.REQUIRED)
    @NotBlank
    private String refreshToken;
}
