package com.tikectsystem.filter;

import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.fastjson.JSON;
import com.tikectsystem.common.ApiResponse;
import com.tikectsystem.enums.BaseCode;
import com.tikectsystem.properties.BackManageProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * 后台登录状态过滤器。
 * <p>
 * 过滤器同时支持后台请求头和后台路径匹配，避免调用方漏传请求头时绕过后台登录态。
 **/
@WebFilter(value = "/*", filterName = "backManageAuthFilter")
public class BackManageAuthFilter extends OncePerRequestFilter {

    private static final String BACK_MANAGE_HEADER = "back_manage";

    private static final String TRUE_VALUE = "true";

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    
    private final BackManageProperties backManageProperties;
    
    public BackManageAuthFilter(BackManageProperties backManageProperties) {
        this.backManageProperties = backManageProperties;
    }
    
    @Override
    protected void doFilterInternal(@NonNull final HttpServletRequest request, 
                                    @NonNull final HttpServletResponse response,
                                    @NonNull final FilterChain filterChain) throws ServletException, IOException {
        String requestUri = request.getRequestURI();
        if (!isBackManageRequest(request, requestUri)) {
            filterChain.doFilter(request, response);
            return;
        }
        boolean pass = false;
        // 如果是后台登录等排除路径，则直接放行。
        if (matches(backManageProperties.getLoginExcludeApi(), requestUri)) {
            pass = true;
        }else {
            // 检查后台登录状态。
            if (StpUtil.isLogin()){
                pass = true;
            }
        }
        if (pass) {
            filterChain.doFilter(request, response);
        }else {
            // 用户未登录，返回统一 JSON 错误响应。
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json; charset=utf-8");
            try (PrintWriter writer = response.getWriter()) {
                writer.print(JSON.toJSONString(ApiResponse.error(BaseCode.USER_NOT_LOGIN)));
            }
        }
    }

    private boolean isBackManageRequest(HttpServletRequest request, String requestUri) {
        return TRUE_VALUE.equals(request.getHeader(BACK_MANAGE_HEADER)) ||
                matches(backManageProperties.getAuthIncludeApi(), requestUri);
    }

    private boolean matches(List<String> patterns, String requestUri) {
        if (patterns == null || patterns.isEmpty()) {
            return false;
        }
        for (String pattern : patterns) {
            if (PATH_MATCHER.match(pattern, requestUri)) {
                return true;
            }
        }
        return false;
    }
}
