package com.tikectsystem.util;

import org.apache.commons.lang.StringUtils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 远程工具
 * @author: 阿星不是程序员
 **/
public class RemoteUtil {

    private static final String UNKNOWN = "unknown";
    
    public static String getRemoteId(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        String forward = request.getHeader("X-Forwarded-For");
        String ip = getRemoteIpFromForward(forward);
        if (StringUtils.isBlank(ip)) {
            ip = request.getRemoteAddr();
        }
        if (StringUtils.isBlank(ip)) {
            ip = UNKNOWN;
        }
        String ua = StringUtils.trimToEmpty(request.getHeader("user-agent"));
        return ip + ua;
    }
    
    private static String getRemoteIpFromForward(String forward) {
        if (StringUtils.isNotBlank(forward)) {
            String[] ipList = forward.split(",");
            for (String ip : ipList) {
                String trimIp = StringUtils.trim(ip);
                if (StringUtils.isNotBlank(trimIp) && !UNKNOWN.equalsIgnoreCase(trimIp)) {
                    return trimIp;
                }
            }
        }
        return null;
    }
}
