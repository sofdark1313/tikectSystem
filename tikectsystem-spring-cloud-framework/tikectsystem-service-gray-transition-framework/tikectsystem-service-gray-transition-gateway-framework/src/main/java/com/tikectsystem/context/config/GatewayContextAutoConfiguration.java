package com.tikectsystem.context.config;

import com.tikectsystem.context.ContextHandler;
import com.tikectsystem.context.filter.GatewayWorkClearFilter;
import com.tikectsystem.context.filter.GatewayWorkRouteFilter;
import com.tikectsystem.context.impl.GatewayContextHandler;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: Gateway配置
 * @author: 阿星不是程序员
 **/
public class GatewayContextAutoConfiguration {
    
    @Bean
    public GlobalFilter gatewayWorkRouteFilter() {
        return new GatewayWorkRouteFilter();
    }
    
    @Bean
    public GlobalFilter gatewayWorkClearFilter() {
        return new GatewayWorkClearFilter();
    }
    
    @Bean
    public ContextHandler webMvcContext(){
        return new GatewayContextHandler();
    }
}
