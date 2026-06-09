package com.tikectsystem.service;

import com.tikectsystem.client.BaseDataClient;
import com.tikectsystem.common.ApiResponse;
import com.tikectsystem.core.RedisKeyManage;
import com.tikectsystem.dto.GetChannelDataByCodeDto;
import com.tikectsystem.enums.BaseCode;
import com.tikectsystem.exception.ArgumentError;
import com.tikectsystem.exception.ArgumentException;
import com.tikectsystem.exception.TikectsystemFrameException;
import com.tikectsystem.redis.RedisCache;
import com.tikectsystem.redis.RedisKeyBuild;
import com.tikectsystem.util.StringUtil;
import com.tikectsystem.vo.GetChannelDataVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.tikectsystem.constant.GatewayConstant.CODE;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 渠道数据获取
 * @author: 阿星不是程序员
 **/
@Slf4j
@Service
public class ChannelDataService {
    
    private final static String EXCEPTION_MESSAGE = "code参数为空";
    
    @Lazy
    @Autowired
    private BaseDataClient baseDataClient;
    
    @Autowired
    private RedisCache redisCache;
    
    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;
    
    public void checkCode(String code){
        if (StringUtil.isEmpty(code)) {
            ArgumentError argumentError = new ArgumentError();
            argumentError.setArgumentName(CODE);
            argumentError.setMessage(EXCEPTION_MESSAGE);
            List<ArgumentError> argumentErrorList = new ArrayList<>();
            argumentErrorList.add(argumentError);
            throw new ArgumentException(BaseCode.ARGUMENT_EMPTY.getCode(),argumentErrorList);
        }
    }
    
    public GetChannelDataVo getChannelDataByCode(String code){
        checkCode(code);
        GetChannelDataVo channelDataVo = getChannelDataByRedis(code);
        if (Objects.isNull(channelDataVo)) {
            channelDataVo = getChannelDataByClient(code);
            setChannelDataRedis(code,channelDataVo);
        }
        return channelDataVo;
    }
    
    private GetChannelDataVo getChannelDataByRedis(String code){
        return redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.CHANNEL_DATA,code),GetChannelDataVo.class);
    }
    
    private void setChannelDataRedis(String code,GetChannelDataVo getChannelDataVo){
        redisCache.set(RedisKeyBuild.createRedisKey(RedisKeyManage.CHANNEL_DATA,code),getChannelDataVo);
    }
    
    private GetChannelDataVo getChannelDataByClient(String code){
        GetChannelDataByCodeDto getChannelDataByCodeDto = new GetChannelDataByCodeDto();
        getChannelDataByCodeDto.setCode(code);
        
        Future<ApiResponse<GetChannelDataVo>> future = 
                threadPoolExecutor.submit(() -> baseDataClient.getByCode(getChannelDataByCodeDto));
        try {
            ApiResponse<GetChannelDataVo> getChannelDataApiResponse = future.get(10, TimeUnit.SECONDS);
            if (Objects.nonNull(getChannelDataApiResponse) && Objects.equals(getChannelDataApiResponse.getCode(), BaseCode.SUCCESS.getCode())) {
                GetChannelDataVo channelDataVo = getChannelDataApiResponse.getData();
                if (Objects.nonNull(channelDataVo)) {
                    return channelDataVo;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            log.error("baseDataClient getByCode Interrupted",e);
            throw new TikectsystemFrameException(BaseCode.THREAD_INTERRUPTED);
        } catch (ExecutionException e) {
            log.error("baseDataClient getByCode execution exception",e);
            throw new TikectsystemFrameException(BaseCode.SYSTEM_ERROR);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.error("baseDataClient getByCode timeout exception",e);
            throw new TikectsystemFrameException(BaseCode.EXECUTE_TIME_OUT);
        }
        
        throw new TikectsystemFrameException(BaseCode.CHANNEL_DATA_NOT_EXIST);
    }
}
