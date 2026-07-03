package com.tikectsystem.service;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.fastjson2.JSON;
import com.tikectsystem.dto.BackManageLoginDto;
import com.tikectsystem.exception.TikectsystemFrameException;
import com.tikectsystem.properties.BackManageProperties;
import com.tikectsystem.vo.BackManageLoginVo;
import com.tikectsystem.vo.BackManageUserDetailVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 后台管理登录 service
 * @author: 阿星不是程序员
 **/
@Service
public class BackManageUserService {
    
    @Autowired
    private BackManageProperties backManageProperties;
    

    public BackManageLoginVo login(BackManageLoginDto backManageLoginDto){
        //验证用户信息
        verifyUser(backManageLoginDto);
        //登录
        StpUtil.login(backManageLoginDto.getUsername());
        SaSession session = StpUtil.getSession();
        BackManageUserDetailVo backManageUserDetailVo = new BackManageUserDetailVo();
        backManageUserDetailVo.setUserId("1");
        backManageUserDetailVo.setHomePath("/");
        backManageUserDetailVo.setRealName("阿星不是程序员");
        backManageUserDetailVo.setDesc("javaup@yeah.net");
        backManageUserDetailVo.setUsername(backManageLoginDto.getUsername());
        backManageUserDetailVo.setAvatar("https://multimedia-javaup.cn/%E5%A4%A7%E9%BA%A6pro/dog.png");
        session.set("userDetail", JSON.toJSONString(backManageUserDetailVo));
        BackManageLoginVo backManageLoginVo = new BackManageLoginVo();
        backManageLoginVo.setId("1");
        backManageLoginVo.setRealName("阿星不是程序员");
        backManageLoginVo.setUsername(backManageLoginDto.getUsername());
        backManageLoginVo.setAccessToken(StpUtil.getTokenValue());
        return backManageLoginVo;
    }
    
    public BackManageUserDetailVo userInfo() {
        Object userDetailObj = StpUtil.getSession().get("userDetail");
        if (userDetailObj == null) {
            throw new TikectsystemFrameException("用户未登录或登录已过期");
        }
        BackManageUserDetailVo backManageUserDetailVo;
        try {
            backManageUserDetailVo = JSON.parseObject(String.valueOf(userDetailObj), BackManageUserDetailVo.class);
        } catch (Exception e) {
            throw new TikectsystemFrameException("user not login or login expired");
        }
        if (backManageUserDetailVo == null) {
            throw new TikectsystemFrameException("user not login or login expired");
        }
        return backManageUserDetailVo;
    }
    
    public void verifyUser(BackManageLoginDto backManageLoginDto){
        if (!Objects.equals(backManageProperties.getUsername(), backManageLoginDto.getUsername())) {
            throw new TikectsystemFrameException("用户名错误");
        }
        if (!Objects.equals(backManageProperties.getPassword(), backManageLoginDto.getPassword())) {
            throw new TikectsystemFrameException("用户密码错误");
        }
    }
}
