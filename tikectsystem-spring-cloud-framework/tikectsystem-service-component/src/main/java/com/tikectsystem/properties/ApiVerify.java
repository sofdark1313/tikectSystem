package com.tikectsystem.properties;

import com.tikectsystem.enums.BaseCode;
import com.tikectsystem.exception.TikectsystemFrameException;
import com.tikectsystem.util.StringUtil;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Objects;

/**
 * 敏感内部 API 调用密码校验器。
 * <p>
 * 用于保护渠道配置、对账任务等不应由普通前台直接调用的接口。
 **/
public class ApiVerify {
    
    private static final String API_PASSWORD = "api_password";
    
    private final BackManageProperties backManageProperties;
    
    public ApiVerify(BackManageProperties backManageProperties) {
        this.backManageProperties = backManageProperties;
    }

    /**
     * 校验敏感内部 API 调用密码。
     */
    public void verifyApi() {
        if (Boolean.TRUE.equals(backManageProperties.getApiPasswordCall())) {
            if (StringUtil.isEmpty(backManageProperties.getApiPassword())) {
                throw new TikectsystemFrameException(BaseCode.API_CALL_NEED_PASSWORD);
            }
            String password = getApiPasswordHeader();
            if (StringUtil.isEmpty(password)) {
                throw new TikectsystemFrameException(BaseCode.API_CALL_NEED_PASSWORD);
            }
            if (!Objects.equals(password, backManageProperties.getApiPassword())) {
                throw new TikectsystemFrameException(BaseCode.API_CALL_PASSWORD_ERROR);
            }
        }
    }

    private String getApiPasswordHeader() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest().getHeader(API_PASSWORD);
        }
        return null;
    }
}
