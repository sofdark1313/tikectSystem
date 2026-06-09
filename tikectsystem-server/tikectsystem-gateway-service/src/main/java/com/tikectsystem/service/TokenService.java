package com.tikectsystem.service;

import com.alibaba.fastjson.JSONObject;
import com.tikectsystem.core.RedisKeyManage;
import com.tikectsystem.util.StringUtil;
import com.tikectsystem.enums.BaseCode;
import com.tikectsystem.exception.TikectsystemFrameException;
import com.tikectsystem.jwt.TokenUtil;
import com.tikectsystem.redis.RedisCache;
import com.tikectsystem.redis.RedisKeyBuild;
import com.tikectsystem.vo.UserVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: token数据获取
 * @author: 阿星不是程序员
 **/

@Component
public class TokenService {
    
    @Autowired
    private RedisCache redisCache;
    
    public String parseToken(String token,String tokenSecret){
        String userStr = TokenUtil.parseToken(token,tokenSecret);
        if (StringUtil.isNotEmpty(userStr)) {
            return parseUserId(userStr);
        }
        return null;
    }

    private String parseUserId(String userStr) {
        try {
            JSONObject user = JSONObject.parseObject(userStr);
            return user == null ? null : user.getString("userId");
        } catch (Exception e) {
            return null;
        }
    }
    
    public UserVo getUser(String token,String code,String tokenSecret){
        UserVo userVo = null;
        String userId = parseToken(token,tokenSecret);
        if (StringUtil.isNotEmpty(userId)) {
            userVo = redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.USER_LOGIN, code, userId), UserVo.class);
        }
        return Optional.ofNullable(userVo).orElseThrow(() -> new TikectsystemFrameException(BaseCode.LOGIN_USER_NOT_EXIST));
    }
}
