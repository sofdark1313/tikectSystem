package com.tikectsystem.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 后台管理配置属性。
 * <p>
 * 后台登录账号、API 调用密码等敏感配置必须由部署环境显式提供，避免使用弱默认值。
 **/
@Data
@ConfigurationProperties(prefix = BackManageProperties.MANAGE)
public class BackManageProperties {
    
    public static final String MANAGE = "manage";

    /**
     * 后台登录用户名，未配置时禁止后台登录。
     */
    private String username;

    /**
     * 后台登录密码，未配置时禁止后台登录。
     */
    private String password;

    /**
     * 后台鉴权排除路径，例如登录接口。
     */
    private List<String> loginExcludeApi = List.of("/auth/login");

    /**
     * 后台接口保护路径。匹配这些路径时，即使请求未携带 back_manage 请求头，也必须校验后台登录态。
     */
    private List<String> authIncludeApi = List.of("/auth/**", "/program/manage/**", "/order/manage/**", "/area/manage/**");

    /**
     * 是否启用敏感内部 API 的调用密码校验，默认启用以避免未配置时开放敏感操作。
     */
    private Boolean apiPasswordCall = true;

    /**
     * 敏感内部 API 调用密码。
     */
    private String apiPassword;

    /**
     * 后台用户展示 ID。
     */
    private String userId = "1";

    /**
     * 后台用户展示名称。
     */
    private String realName = "后台管理员";

    /**
     * 后台用户展示描述。
     */
    private String description = "系统管理账号";

    /**
     * 后台用户头像地址。
     */
    private String avatar = "";

    /**
     * 后台登录后的默认首页路径。
     */
    private String homePath = "/";
}
