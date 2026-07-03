package com.tikectsystem.service;

import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tikectsystem.core.RedisKeyManage;
import com.tikectsystem.dto.ChannelDataAddDto;
import com.tikectsystem.dto.GetChannelDataByCodeDto;
import com.tikectsystem.entity.ChannelTableData;
import com.tikectsystem.enums.BaseCode;
import com.tikectsystem.enums.Status;
import com.tikectsystem.exception.TikectsystemFrameException;
import com.tikectsystem.mapper.ChannelDataMapper;
import com.tikectsystem.redis.RedisCache;
import com.tikectsystem.redis.RedisKeyBuild;
import com.tikectsystem.util.DateUtils;
import com.tikectsystem.vo.GetChannelDataVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 渠道 service
 * @author: 阿星不是程序员
 **/
@Service
@Slf4j
public class ChannelDataService {
    
    @Autowired
    private ChannelDataMapper channelDataMapper;
    
    @Autowired
    private UidGenerator uidGenerator;
    
    @Autowired
    private RedisCache redisCache;
    
    public GetChannelDataVo getByCode(GetChannelDataByCodeDto dto){
        LambdaQueryWrapper<ChannelTableData> wrapper = Wrappers.lambdaQuery(ChannelTableData.class)
                .eq(ChannelTableData::getStatus, Status.RUN.getCode())
                .eq(ChannelTableData::getCode,dto.getCode());
        ChannelTableData channelData = channelDataMapper.selectOne(wrapper);
        if (Objects.isNull(channelData)) {
            throw new TikectsystemFrameException(BaseCode.CHANNEL_DATA_NOT_EXIST);
        }
        GetChannelDataVo getChannelDataVo = new GetChannelDataVo();
        BeanUtils.copyProperties(channelData,getChannelDataVo);
        return getChannelDataVo;
    }
    
    @Transactional(rollbackFor = Exception.class)
    public void add(ChannelDataAddDto channelDataAddDto) {
        ChannelTableData channelData = new ChannelTableData();
        BeanUtils.copyProperties(channelDataAddDto,channelData);
        channelData.setId(uidGenerator.getUid());
        channelData.setCreateTime(DateUtils.now());
        channelDataMapper.insert(channelData);
        addRedisChannelData(channelData);
    }
    
    private void addRedisChannelData(ChannelTableData channelData){
        GetChannelDataVo getChannelDataVo = new GetChannelDataVo();
        BeanUtils.copyProperties(channelData,getChannelDataVo);
        redisCache.set(RedisKeyBuild.createRedisKey(RedisKeyManage.CHANNEL_DATA,getChannelDataVo.getCode()),getChannelDataVo);
    }
}
