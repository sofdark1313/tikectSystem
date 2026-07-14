package com.tikectsystem.service;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tikectsystem.core.RedisKeyManage;
import com.tikectsystem.dto.TicketUserDto;
import com.tikectsystem.dto.TicketUserIdDto;
import com.tikectsystem.dto.TicketUserListDto;
import com.tikectsystem.entity.TicketUser;
import com.tikectsystem.entity.User;
import com.tikectsystem.enums.BaseCode;
import com.tikectsystem.exception.TikectsystemFrameException;
import com.tikectsystem.mapper.TicketUserMapper;
import com.tikectsystem.mapper.UserMapper;
import com.tikectsystem.redis.RedisCache;
import com.tikectsystem.redis.RedisKeyBuild;
import com.tikectsystem.threadlocal.BaseParameterHolder;
import com.tikectsystem.util.StringUtil;
import com.tikectsystem.vo.TicketUserVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

import static com.tikectsystem.constant.Constant.USER_ID;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 购票人 service
 * @author: 阿星不是程序员
 **/
@Service
public class TicketUserService extends ServiceImpl<TicketUserMapper, TicketUser> {
    
    @Autowired
    private TicketUserMapper ticketUserMapper;
    
    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private UidGenerator uidGenerator;
    
    @Autowired
    private RedisCache redisCache;
    
    public List<TicketUserVo> list(TicketUserListDto ticketUserListDto) {
        Long currentUserId = currentLoginUserId();
        // 先从当前登录用户的缓存中查询。
        List<TicketUserVo> ticketUserVoList = redisCache.getValueIsList(RedisKeyBuild.createRedisKey(
                RedisKeyManage.TICKET_USER_LIST, currentUserId), TicketUserVo.class);
        if (CollectionUtil.isNotEmpty(ticketUserVoList)) {
            return ticketUserVoList;
        }
        LambdaQueryWrapper<TicketUser> ticketUserLambdaQueryWrapper = Wrappers.lambdaQuery(TicketUser.class)
                .eq(TicketUser::getUserId, currentUserId);
        List<TicketUser> ticketUsers = ticketUserMapper.selectList(ticketUserLambdaQueryWrapper);
        return BeanUtil.copyToList(ticketUsers,TicketUserVo.class);
    }
    
    @Transactional(rollbackFor = Exception.class)
    public void add(TicketUserDto ticketUserDto) {
        Long currentUserId = currentLoginUserId();
        User user = userMapper.selectById(currentUserId);
        if (Objects.isNull(user)) {
            throw new TikectsystemFrameException(BaseCode.USER_EMPTY);
        }
        LambdaQueryWrapper<TicketUser> ticketUserLambdaQueryWrapper = Wrappers.lambdaQuery(TicketUser.class)
                .eq(TicketUser::getUserId, currentUserId)
                .eq(TicketUser::getIdType, ticketUserDto.getIdType())
                .eq(TicketUser::getIdNumber, ticketUserDto.getIdNumber());
        TicketUser ticketUser = ticketUserMapper.selectOne(ticketUserLambdaQueryWrapper);
        if (Objects.nonNull(ticketUser)) {
            throw new TikectsystemFrameException(BaseCode.TICKET_USER_EXIST);
        }
        TicketUser addTicketUser = new TicketUser();
        BeanUtil.copyProperties(ticketUserDto,addTicketUser);
        addTicketUser.setId(uidGenerator.getUid());
        addTicketUser.setUserId(currentUserId);
        ticketUserMapper.insert(addTicketUser);
        delTicketUserVoListCache(String.valueOf(currentUserId));
    }
    @Transactional(rollbackFor = Exception.class)
    public void delete(TicketUserIdDto ticketUserIdDto) {
        Long currentUserId = currentLoginUserId();
        TicketUser ticketUser = ticketUserMapper.selectById(ticketUserIdDto.getId());
        if (Objects.isNull(ticketUser)) {
            throw new TikectsystemFrameException(BaseCode.TICKET_USER_EMPTY);
        }
        if (!Objects.equals(ticketUser.getUserId(), currentUserId)) {
            throw new TikectsystemFrameException(BaseCode.TICKET_USER_EMPTY);
        }
        LambdaQueryWrapper<TicketUser> deleteWrapper = Wrappers.lambdaQuery(TicketUser.class)
                .eq(TicketUser::getId, ticketUserIdDto.getId())
                .eq(TicketUser::getUserId, currentUserId);
        ticketUserMapper.delete(deleteWrapper);
        delTicketUserVoListCache(String.valueOf(currentUserId));
    }
    
    public void delTicketUserVoListCache(String userId){
        redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.TICKET_USER_LIST, userId));
    }

    private Long currentLoginUserId() {
        String userId = BaseParameterHolder.getParameter(USER_ID);
        if (StringUtil.isEmpty(userId)) {
            throw new TikectsystemFrameException(BaseCode.USER_ID_EMPTY);
        }
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException e) {
            throw new TikectsystemFrameException(BaseCode.USER_ID_EMPTY);
        }
    }
}
