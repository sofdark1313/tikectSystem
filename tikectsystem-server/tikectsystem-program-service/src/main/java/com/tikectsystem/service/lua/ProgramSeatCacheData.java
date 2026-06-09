package com.tikectsystem.service.lua;

import com.alibaba.fastjson.JSON;
import com.tikectsystem.redis.RedisCache;
import com.tikectsystem.vo.SeatVo;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 查询节目座位缓存
 * @author: 阿星不是程序员
 **/
@Slf4j
@Component
public class ProgramSeatCacheData {
    
    @Autowired
    private RedisCache redisCache;
    
    private DefaultRedisScript<Object> redisScript;
    
    private static final Integer THRESHOLD_VALUE = 2000;
    
    @PostConstruct
    public void init(){
        try {
            redisScript = new DefaultRedisScript<>();
            redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/programSeat.lua")));
            redisScript.setResultType(Object.class);
        } catch (Exception e) {
            log.error("redisScript init lua error",e);
        }
    }
    
    public List<SeatVo> getData(List<String> keys, String[] args){
        List<SeatVo> list;
        if (redisScript == null) {
            throw new IllegalStateException("programSeat lua script is not initialized");
        }
        Object object = redisCache.getInstance().execute(redisScript, keys, args);
        List<String> seatVoStrlist = new ArrayList<>();
        if (object instanceof List<?> objectList) {
            seatVoStrlist = objectList.stream()
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .collect(Collectors.toList());
        }
        if (seatVoStrlist.size() > THRESHOLD_VALUE) {
            list = seatVoStrlist.parallelStream()
                    .map(this::parseSeatVo)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }else {
            list = seatVoStrlist.stream()
                    .map(this::parseSeatVo)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
        return list;
    }

    private SeatVo parseSeatVo(String seatVoStr) {
        try {
            return JSON.parseObject(seatVoStr, SeatVo.class);
        } catch (Exception e) {
            log.warn("seat cache data parse error, seatVoStr : {}", seatVoStr, e);
            return null;
        }
    }
}
