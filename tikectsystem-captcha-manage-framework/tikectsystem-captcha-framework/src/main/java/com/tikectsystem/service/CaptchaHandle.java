package com.tikectsystem.service;

import com.tikectsystem.captcha.model.common.ResponseModel;
import com.tikectsystem.captcha.model.vo.CaptchaVO;
import com.tikectsystem.captcha.service.CaptchaService;
import com.tikectsystem.util.RemoteUtil;
import lombok.AllArgsConstructor;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 验证码处理器
 * @author: 阿星不是程序员
 **/
@AllArgsConstructor
public class CaptchaHandle {
    
    private final CaptchaService captchaService;
    
    public ResponseModel getCaptcha(CaptchaVO captchaVO) {
        captchaVO.setBrowserInfo(RemoteUtil.getRemoteId(getCurrentRequest()));
        return captchaService.get(captchaVO);
    }
    
    public ResponseModel checkCaptcha(CaptchaVO captchaVO) {
        captchaVO.setBrowserInfo(RemoteUtil.getRemoteId(getCurrentRequest()));
        return captchaService.check(captchaVO);
    }
    
    public ResponseModel verification(CaptchaVO captchaVO) {
        return captchaService.verification(captchaVO);
    }

    private HttpServletRequest getCurrentRequest() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }
}
