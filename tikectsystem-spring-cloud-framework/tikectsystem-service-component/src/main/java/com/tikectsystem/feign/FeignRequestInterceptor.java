package com.tikectsystem.feign;

import com.tikectsystem.threadlocal.BaseParameterHolder;
import com.tikectsystem.util.StringUtil;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

import static com.tikectsystem.constant.Constant.CODE;
import static com.tikectsystem.constant.Constant.GRAY_PARAMETER;
import static com.tikectsystem.constant.Constant.TRACE_ID;


/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: feign 参数传递
 * @author: 阿星不是程序员
 **/

@Slf4j
@AllArgsConstructor
public class FeignRequestInterceptor implements RequestInterceptor {

    private final String serverGray;

    @Override
    public void apply(RequestTemplate template) {
        try {
            RequestAttributes ra = RequestContextHolder.getRequestAttributes();
            String traceId = null;
            String code = null;
            String gray = null;
            if (ra instanceof ServletRequestAttributes sra) {
                HttpServletRequest request = sra.getRequest();
                traceId = request.getHeader(TRACE_ID);
                code = request.getHeader(CODE);
                gray = request.getHeader(GRAY_PARAMETER);
            }
            if (StringUtil.isEmpty(traceId)) {
                traceId = BaseParameterHolder.getParameter(TRACE_ID);
            }
            if (StringUtil.isEmpty(code)) {
                code = BaseParameterHolder.getParameter(CODE);
            }
            if (StringUtil.isEmpty(gray)) {
                gray = BaseParameterHolder.getParameter(GRAY_PARAMETER);
            }
            if (StringUtil.isEmpty(gray)) {
                gray = serverGray;
            }
            addHeaderIfNotEmpty(template, TRACE_ID, traceId);
            addHeaderIfNotEmpty(template, CODE, code);
            addHeaderIfNotEmpty(template, GRAY_PARAMETER, gray);
        }catch (Exception e) {
            log.error("FeignRequestInterceptor apply error",e);
        }
    }

    private void addHeaderIfNotEmpty(RequestTemplate template, String name, String value) {
        if (StringUtil.isNotEmpty(value)) {
            template.header(name,value);
        }
    }
}
