package com.tikectsystem.properties;

import com.tikectsystem.enums.BaseCode;
import com.tikectsystem.exception.TikectsystemFrameException;
import com.tikectsystem.util.StringUtil;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Objects;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 后台管理配置属性
 * @author: 阿星不是程序员
 **/
public class ApiVerify {
    
    private static final String API_PASSWORD = "api_password";
    
    private final BackManageProperties backManageProperties;
    
    public ApiVerify(BackManageProperties backManageProperties) {
        this.backManageProperties = backManageProperties;
    }
    
    public void verifyApi() {
        if (Boolean.TRUE.equals(backManageProperties.getApiPasswordCall())) {
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
