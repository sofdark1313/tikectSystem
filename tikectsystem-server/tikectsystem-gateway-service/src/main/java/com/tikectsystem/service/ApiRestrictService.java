package com.tikectsystem.service;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DateUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baidu.fsg.uid.UidGenerator;
import com.tikectsystem.core.RedisKeyManage;
import com.tikectsystem.util.StringUtil;
import com.tikectsystem.dto.ApiDataDto;
import com.tikectsystem.enums.ApiRuleType;
import com.tikectsystem.enums.BaseCode;
import com.tikectsystem.enums.RuleTimeUnit;
import com.tikectsystem.exception.TikectsystemFrameException;
import com.tikectsystem.kafka.ApiDataMessageSend;
import com.tikectsystem.property.GatewayProperty;
import com.tikectsystem.redis.RedisCache;
import com.tikectsystem.redis.RedisKeyBuild;
import com.tikectsystem.service.lua.ApiRestrictCacheOperate;
import com.tikectsystem.util.DateUtils;
import com.tikectsystem.vo.DepthRuleVo;
import com.tikectsystem.vo.RuleVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 接口请求记录
 * @author: 阿星不是程序员
 **/
@Slf4j
@Component
public class ApiRestrictService {

    private static final PathMatcher PATH_MATCHER = new AntPathMatcher();
    
    @Autowired
    private RedisCache redisCache;
    
    @Autowired
    private GatewayProperty gatewayProperty;
    
    @Autowired(required = false)
    private ApiDataMessageSend apiDataMessageSend;
    
    @Autowired
    private ApiRestrictCacheOperate apiRestrictCacheOperate;
    
    @Autowired
    private UidGenerator uidGenerator;
    
    public boolean checkApiRestrict(String requestUri){
        if (gatewayProperty.getApiRestrictPaths() != null) {
            for (String apiRestrictPath : gatewayProperty.getApiRestrictPaths()) {
                if(PATH_MATCHER.match(apiRestrictPath, requestUri)){
                    return true;
                }
            }
        }
        return false;
    }

    public void apiRestrict(String id, String url, ServerHttpRequest request) {
        //请求的路径在配置范围内的话
        if (checkApiRestrict(url)) {
            long triggerResult = 0L;
            long triggerCallStat = 0L;
            long apiCount;
            long threshold;
            long messageIndex;
            String message = "";
            //获得请求客户端地址
            String ip = getIpAddress(request);

            StringBuilder stringBuilder = new StringBuilder(ip);
            if (StringUtil.isNotEmpty(id)) {
                stringBuilder.append("_").append(id);
            }
            String commonKey = stringBuilder.append("_").append(url).toString();
            try {
                List<DepthRuleVo> depthRuleVoList = new ArrayList<>();
                //查询规则 Hash结构
                //普通规则
                RuleVo ruleVo = redisCache.getForHash(RedisKeyBuild.createRedisKey(RedisKeyManage.ALL_RULE_HASH), RedisKeyBuild.createRedisKey(RedisKeyManage.RULE).getRelKey(),RuleVo.class);
                //深度规则
                String depthRuleStr = redisCache.getForHash(RedisKeyBuild.createRedisKey(RedisKeyManage.ALL_RULE_HASH), RedisKeyBuild.createRedisKey(RedisKeyManage.DEPTH_RULE).getRelKey(),String.class);
                if (StringUtil.isNotEmpty(depthRuleStr)) {
                    depthRuleVoList = JSON.parseArray(depthRuleStr,DepthRuleVo.class);
                }
                //规则类型 0：不存在 1：普通规则 2：深度规则
                int apiRuleType = ApiRuleType.NO_RULE.getCode();
                if (Optional.ofNullable(ruleVo).isPresent()) {
                    apiRuleType = ApiRuleType.RULE.getCode();
                    message = ruleVo.getMessage();
                }
                if (Optional.ofNullable(ruleVo).isPresent() && CollectionUtil.isNotEmpty(depthRuleVoList)) {
                    apiRuleType = ApiRuleType.DEPTH_RULE.getCode();
                }
                if (apiRuleType == ApiRuleType.RULE.getCode() || apiRuleType == ApiRuleType.DEPTH_RULE.getCode()) {

                    if (ruleVo == null) {
                        log.warn("api restrict rule missing, apiRuleType: {}, key: {}",apiRuleType,commonKey);
                        return;
                    }
                    //普通规则构建
                    JSONObject parameter = getRuleParameter(apiRuleType,commonKey,ruleVo);

                    if (apiRuleType == ApiRuleType.DEPTH_RULE.getCode()) {
                        //深度规则构建
                        depthRuleVoList = sortStartTimeWindow(depthRuleVoList);
                        parameter = getDepthRuleParameter(parameter,commonKey,depthRuleVoList);
                    }
                    ApiRestrictData apiRestrictData = apiRestrictCacheOperate
                            .apiRuleOperate(Collections.singletonList(JSON.toJSONString(parameter)), new Object[]{});
                    //是否需要进行限制
                    triggerResult = apiRestrictData.getTriggerResult();
                    //是否进行保存记录
                    triggerCallStat = apiRestrictData.getTriggerCallStat();
                    //请求数
                    apiCount = apiRestrictData.getApiCount();
                    //规则阈值
                    threshold = apiRestrictData.getThreshold();
                    //定制的规则提示语
                    messageIndex = apiRestrictData.getMessageIndex();
                    int depthRuleIndex = (int) messageIndex - 1;
                    if (depthRuleIndex >= 0 && depthRuleIndex < depthRuleVoList.size()) {
                        message = Optional.ofNullable(depthRuleVoList.get(depthRuleIndex))
                                .map(DepthRuleVo::getMessage)
                                .filter(StringUtil::isNotEmpty)
                                .orElse(message);
                    }
                    log.debug("api rule [key : {}], [triggerResult : {}], [triggerCallStat : {}], [apiCount : {}], [threshold : {}]",commonKey,triggerResult,triggerCallStat,apiCount,threshold);
                }
            }catch (Exception e) {
                log.error("redis Lua eror", e);
            }
            if (triggerResult == 1) {
                if (triggerCallStat == ApiRuleType.RULE.getCode() || triggerCallStat == ApiRuleType.DEPTH_RULE.getCode()) {
                    saveApiData(request, url, (int)triggerCallStat);
                }
                String defaultMessage = BaseCode.API_RULE_TRIGGER.getMsg();
                if (StringUtil.isNotEmpty(message)) {
                    defaultMessage = message;
                }
                throw new TikectsystemFrameException(BaseCode.API_RULE_TRIGGER.getCode(),defaultMessage);
            }
        }
    }

    /**
     * 普通规则构建
     * */
    public JSONObject getRuleParameter(int apiRuleType, String commonKey, RuleVo ruleVo){
        JSONObject parameter = new JSONObject();

        parameter.put("apiRuleType",apiRuleType);
        //普通规则中要进行统计请求数
        String ruleKey = "rule_api_limit" + "_" + commonKey;
        parameter.put("ruleKey",ruleKey);
        //普通规则中进行统计的时间
        parameter.put("statTime",String.valueOf(Objects.equals(ruleVo.getStatTimeType(), RuleTimeUnit.SECOND.getCode()) ? ruleVo.getStatTime() : ruleVo.getStatTime() * 60));
        //普通规则中进行统计的阈值
        parameter.put("threshold",ruleVo.getThreshold());
        //普通规则超过阈值后限制的时间
        parameter.put("effectiveTime",String.valueOf(Objects.equals(ruleVo.getEffectiveTimeType(), RuleTimeUnit.SECOND.getCode()) ? ruleVo.getEffectiveTime() : ruleVo.getEffectiveTime() * 60));
        //实现普通规则执行限制
        parameter.put("ruleLimitKey", RedisKeyBuild.createRedisKey(RedisKeyManage.RULE_LIMIT,commonKey).getRelKey());
        //进行统计超过普通规则的数量sorted set结构
        parameter.put("zSetRuleStatKey", RedisKeyBuild.createRedisKey(RedisKeyManage.Z_SET_RULE_STAT,commonKey).getRelKey());

        return parameter;
    }

    /**
     * 深度规则构建
     * */
    public JSONObject getDepthRuleParameter(JSONObject parameter,String commonKey,List<DepthRuleVo> depthRuleVoList){
        //深度规则构建
        //将限制时间窗口进行排序
        //深度规则的个数
        parameter.put("depthRuleSize",String.valueOf(depthRuleVoList.size()));
        //当前时间戳
        parameter.put("currentTime",System.currentTimeMillis());

        List<JSONObject> depthRules = new ArrayList<>();
        for (int i = 0; i < depthRuleVoList.size(); i++) {
            JSONObject depthRule = new JSONObject();
            DepthRuleVo depthRuleVo = depthRuleVoList.get(i);
            //深度规则中进行统计的时间
            depthRule.put("statTime",Objects.equals(depthRuleVo.getStatTimeType(), RuleTimeUnit.SECOND.getCode()) ? depthRuleVo.getStatTime() : depthRuleVo.getStatTime() * 60);
            //深度规则中进行统计的阈值
            depthRule.put("threshold",depthRuleVo.getThreshold());
            //深度规则超过阈值后限制的时间
            depthRule.put("effectiveTime",String.valueOf(Objects.equals(depthRuleVo.getEffectiveTimeType(), RuleTimeUnit.SECOND.getCode()) ? depthRuleVo.getEffectiveTime() : depthRuleVo.getEffectiveTime() * 60));
            //实现深度规则执行限制
            depthRule.put("depthRuleLimit", RedisKeyBuild.createRedisKey(RedisKeyManage.DEPTH_RULE_LIMIT,i,commonKey).getRelKey());
            //深度规则限制开始时间窗口
            depthRule.put("startTimeWindowTimestamp",depthRuleVo.getStartTimeWindowTimestamp());
            //深度规则限制结束时间窗口
            depthRule.put("endTimeWindowTimestamp",depthRuleVo.getEndTimeWindowTimestamp());

            depthRules.add(depthRule);
        }

        parameter.put("depthRules",depthRules);

        return parameter;
    }
    
    public List<DepthRuleVo> sortStartTimeWindow(List<DepthRuleVo> depthRuleVoList){
        return depthRuleVoList.stream().filter(Objects::nonNull).peek(depthRuleVo -> {
            depthRuleVo.setStartTimeWindowTimestamp(getTimeWindowTimestamp(depthRuleVo.getStartTimeWindow()));
            depthRuleVo.setEndTimeWindowTimestamp((getTimeWindowTimestamp(depthRuleVo.getEndTimeWindow())));
        }).filter(depthRuleVo -> {
            boolean valid = depthRuleVo.getStartTimeWindowTimestamp() > 0
                    && depthRuleVo.getEndTimeWindowTimestamp() > 0
                    && depthRuleVo.getStartTimeWindowTimestamp() <= depthRuleVo.getEndTimeWindowTimestamp();
            if (!valid) {
                log.warn("skip invalid depth rule time window, startTimeWindow: {}, endTimeWindow: {}",
                        depthRuleVo.getStartTimeWindow(),depthRuleVo.getEndTimeWindow());
            }
            return valid;
        }).sorted(Comparator.comparing(DepthRuleVo::getStartTimeWindowTimestamp)).collect(Collectors.toList());
    }
    
    public long getTimeWindowTimestamp(String timeWindow){
        if (StringUtil.isEmpty(timeWindow)) {
            return 0L;
        }
        String today = DateUtil.today();
        try {
            return DateUtil.parse(today + " " + timeWindow).getTime();
        } catch (RuntimeException e) {
            log.warn("parse api restrict time window failed, timeWindow: {}",timeWindow,e);
            return 0L;
        }
    }
    
    /**
      * 获取请求的归属IP地址
      *
      * @param request 请求
      */
    public static String getIpAddress(ServerHttpRequest request) {
        String unknown = "unknown";
        String split = ",";
        HttpHeaders headers = request.getHeaders();
        String ip = headers.getFirst("x-forwarded-for");
        if (ip != null && ip.length() != 0 && !unknown.equalsIgnoreCase(ip)) {
            // 多次反向代理后会有多个ip值，第一个ip才是真实ip
            if (ip.contains(split)) {
                ip = ip.split(split)[0];
            }
        }
        if (ip == null || ip.length() == 0 || unknown.equalsIgnoreCase(ip)) {
            ip = headers.getFirst("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || unknown.equalsIgnoreCase(ip)) {
            ip = headers.getFirst("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || unknown.equalsIgnoreCase(ip)) {
            ip = headers.getFirst("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.length() == 0 || unknown.equalsIgnoreCase(ip)) {
            ip = headers.getFirst("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.length() == 0 || unknown.equalsIgnoreCase(ip)) {
            ip = headers.getFirst("X-Real-IP");
        }
        if (ip == null || ip.length() == 0 || unknown.equalsIgnoreCase(ip)) {
            ip = Optional.ofNullable(request.getRemoteAddress())
                    .map(remoteAddress -> remoteAddress.getAddress())
                    .map(address -> address.getHostAddress())
                    .orElse(unknown);
        }
        return ip;
    }
    
    public void saveApiData(ServerHttpRequest request, String apiUrl, Integer type){
        ApiDataDto apiDataDto = new ApiDataDto();
        apiDataDto.setId(uidGenerator.getUid());
        apiDataDto.setApiAddress(getIpAddress(request));
        apiDataDto.setApiUrl(apiUrl);
        apiDataDto.setCreateTime(DateUtils.now());
        apiDataDto.setCallDayTime(DateUtils.nowStr(DateUtils.FORMAT_DATE));
        apiDataDto.setCallHourTime(DateUtils.nowStr(DateUtils.FORMAT_HOUR));
        apiDataDto.setCallMinuteTime(DateUtils.nowStr(DateUtils.FORMAT_MINUTE));
        apiDataDto.setCallSecondTime(DateUtils.nowStr(DateUtils.FORMAT_SECOND));
        apiDataDto.setType(type);
        Optional.ofNullable(apiDataMessageSend).ifPresent(send -> send.sendMessage(JSON.toJSONString(apiDataDto)));
    }
}
