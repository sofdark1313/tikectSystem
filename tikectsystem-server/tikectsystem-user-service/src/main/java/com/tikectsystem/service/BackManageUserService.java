package com.tikectsystem.service;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.fastjson2.JSON;
import com.tikectsystem.dto.BackManageLoginDto;
import com.tikectsystem.exception.TikectsystemFrameException;
import com.tikectsystem.properties.BackManageProperties;
import com.tikectsystem.util.StringUtil;
import com.tikectsystem.vo.BackManageLoginVo;
import com.tikectsystem.vo.BackManageUserDetailVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 后台管理用户服务。
 * <p>
 * 负责后台登录、登录用户详情读取，以及后台登录配置的安全校验。
 **/
@Service
public class BackManageUserService {

    private static final String USER_DETAIL_SESSION_KEY = "userDetail";
    
    @Autowired
    private BackManageProperties backManageProperties;
    
    /**
     * 后台用户登录。
     *
     * @param backManageLoginDto 后台登录参数
     * @return 后台登录结果
     */
    public BackManageLoginVo login(BackManageLoginDto backManageLoginDto){
        // 验证用户信息。
        verifyUser(backManageLoginDto);
        // 登录。
        StpUtil.login(backManageLoginDto.getUsername());
        SaSession session = StpUtil.getSession();
        BackManageUserDetailVo backManageUserDetailVo = buildUserDetail(backManageLoginDto.getUsername());
        session.set(USER_DETAIL_SESSION_KEY, JSON.toJSONString(backManageUserDetailVo));

        // 响应。
        return BackManageLoginVo.builder()
                .id(backManageUserDetailVo.getUserId())
                .realName(backManageUserDetailVo.getRealName())
                .username(backManageLoginDto.getUsername())
                .accessToken(StpUtil.getTokenValue())
                .build();
    }

    /**
     * 查询当前后台登录用户信息。
     *
     * @return 后台用户详情
     */
    public BackManageUserDetailVo userInfo() {
        Object userDetailObj = StpUtil.getSession().get(USER_DETAIL_SESSION_KEY);
        if (userDetailObj == null) {
            throw new TikectsystemFrameException("用户未登录或登录已过期");
        }
        BackManageUserDetailVo backManageUserDetailVo;
        try {
            backManageUserDetailVo = JSON.parseObject(String.valueOf(userDetailObj), BackManageUserDetailVo.class);
        } catch (Exception e) {
            throw new TikectsystemFrameException("用户未登录或登录已过期");
        }
        if (backManageUserDetailVo == null) {
            throw new TikectsystemFrameException("用户未登录或登录已过期");
        }
        return backManageUserDetailVo;
    }

    /**
     * 校验后台登录账号。
     *
     * @param backManageLoginDto 后台登录参数
     */
    public void verifyUser(BackManageLoginDto backManageLoginDto){
        if (StringUtil.isEmpty(backManageProperties.getUsername()) ||
                StringUtil.isEmpty(backManageProperties.getPassword())) {
            throw new TikectsystemFrameException("后台管理账号未配置");
        }
        if (!Objects.equals(backManageProperties.getUsername(), backManageLoginDto.getUsername())) {
            throw new TikectsystemFrameException("用户名错误");
        }
        if (!Objects.equals(backManageProperties.getPassword(), backManageLoginDto.getPassword())) {
            throw new TikectsystemFrameException("用户密码错误");
        }
    }

    private BackManageUserDetailVo buildUserDetail(String username) {
        return BackManageUserDetailVo.builder()
                .userId(backManageProperties.getUserId())
                .homePath(backManageProperties.getHomePath())
                .realName(backManageProperties.getRealName())
                .desc(backManageProperties.getDescription())
                .username(username)
                .avatar(backManageProperties.getAvatar())
                .build();
    }
}
