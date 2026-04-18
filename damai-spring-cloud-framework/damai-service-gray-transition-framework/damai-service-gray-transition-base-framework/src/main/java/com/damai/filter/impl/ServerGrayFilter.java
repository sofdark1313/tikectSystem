package com.damai.filter.impl;


import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.cloud.nacos.NacosServiceInstance;
import com.damai.context.ContextHandler;
import com.damai.enums.BaseCode;
import com.damai.exception.DaMaiFrameException;
import com.damai.filter.AbstractServerFilter;
import com.damai.threadlocal.BaseParameterHolder;
import com.damai.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.damai.constant.Constant.GRAY_FLAG_FALSE;
import static com.damai.constant.Constant.GRAY_FLAG_TRUE;
import static com.damai.constant.Constant.GRAY_PARAMETER;
import static com.damai.constant.Constant.SERVER_GRAY;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 灰度过滤器
 * @author: 阿星不是程序员
 **/

@Slf4j
public class ServerGrayFilter extends AbstractServerFilter {

    /**
     * 此服务的灰度标识
     * */
    @Value(SERVER_GRAY)
    private String serverGray;

    private final ContextHandler contextHandler;

    private final Map<String,String> map = new HashMap<>();

    public ServerGrayFilter(ContextHandler contextHandler){
        this.contextHandler = contextHandler;
        this.map.put(GRAY_FLAG_FALSE, GRAY_FLAG_FALSE);
        this.map.put(GRAY_FLAG_TRUE, GRAY_FLAG_TRUE);
    }


    @Override
    public boolean doFilter(List<? extends ServiceInstance> servers, ServiceInstance server) {
        boolean result;
        try {
            //从请求头获取灰度标识
            String grayFromRequest = Optional.ofNullable(contextHandler.getValueFromHeader(GRAY_PARAMETER))
                    .filter(StringUtil::isNotEmpty)
                    .orElseGet(() -> BaseParameterHolder.getParameter(GRAY_PARAMETER));
            //如果请求头获取灰度标识为空，则从服务配置中获取
            grayFromRequest = Optional.ofNullable(grayFromRequest).filter(StringUtil::isNotEmpty).orElse(serverGray);
            NacosServiceInstance nacosServiceInstance = (NacosServiceInstance)server;
            //获取服务配置中的灰度标识
            String grayFromMetaData = Optional.ofNullable(nacosServiceInstance.getMetadata())
                    .filter(CollectionUtil::isNotEmpty)
                    .map(metadata -> metadata.get(GRAY_PARAMETER))
                    .filter(StringUtil::isNotEmpty)
                    .orElse(GRAY_FLAG_FALSE);
            //判断如果被调用端没有灰度配置则默认配置为生产环境
            grayFromMetaData = Optional.ofNullable(map.get(grayFromMetaData.toLowerCase())).orElse(GRAY_FLAG_FALSE);
            //判断如果请求端没有灰度标识则默认配置为生产环境
            grayFromRequest = Optional.ofNullable(map.get(grayFromRequest.toLowerCase())).orElse(GRAY_FLAG_FALSE);
            //如果请求的灰度标识和被调用服务配置的灰度标识相同，说明服务匹配到了，直接可以调用
            result = grayFromMetaData.equalsIgnoreCase(grayFromRequest);

            /* 如果这时result还是为false，再做一次匹配
             * 如果所有服务端的配置均为spring.cloud.nacos.discovery.metadata.gray=true,而调用请求端的请求头中的 gray 为true，
             * 则也允许结果返回true做负载均衡
             *
             * 反之如果所有服务端的配置为spring.cloud.nacos.discovery.metadata.gray=true,而调用请求端的请求头中的 gray 为false，
             * 则结果返回false,不允许做负载均衡
             */
            if (!result && grayFromRequest.equalsIgnoreCase(GRAY_FLAG_TRUE)) {
                if (CollectionUtil.isEmpty(servers)) {
                    throw new DaMaiFrameException(BaseCode.SERVER_LIST_NOT_EXIST);
                }
                Map<String,String> map = new HashMap<>(servers.size());
                for (ServiceInstance serviceInstance : servers) {
                    NacosServiceInstance instance = (NacosServiceInstance)serviceInstance;
                    //服务中配置的灰度标识
                    String balanceGray = instance.getMetadata().get(GRAY_PARAMETER);
                    //判断如果被调用端没有灰度配置则默认配置为生产环境
                    if (StringUtil.isEmpty(balanceGray) || Objects.isNull(map.get(balanceGray.toLowerCase()))) {
                        balanceGray = GRAY_FLAG_FALSE;
                    }
                    map.put(balanceGray,balanceGray);
                }
                if(Objects.isNull(map.get(GRAY_FLAG_TRUE))) {
                    //能够执行到这里，说明请求是灰度的，要被调用的服务中实例列表都是生产的，可以进行匹配
                    result = true;
                }
            }
        }catch (Exception e) {
            result = false;
            log.error("ServerGrayFilter#doFilter error",e);
        }
        return result;
    }

    @Override
    public int getOrder() {
        return HIGHEST_PRECEDENCE;
    }
}