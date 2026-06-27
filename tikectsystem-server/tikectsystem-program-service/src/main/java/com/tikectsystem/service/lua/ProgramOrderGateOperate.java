package com.tikectsystem.service.lua;

import com.alibaba.fastjson.JSON;
import com.tikectsystem.redis.RedisCache;
import com.tikectsystem.util.StringUtil;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 下单入口轻量准入 Lua 包装。
 */
@Slf4j
@Component
public class ProgramOrderGateOperate {

    @Autowired
    private RedisCache redisCache;

    private DefaultRedisScript<String> redisScript;

    @PostConstruct
    public void init() {
        try {
            redisScript = new DefaultRedisScript<>();
            redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/programOrderGate.lua")));
            redisScript.setResultType(String.class);
        } catch (Exception e) {
            log.error("programOrderGate lua init error", e);
        }
    }

    /**
     * 执行入口准入脚本。
     * @param keys Redis key
     * @param args Lua 参数
     * @return 准入结果
     */
    public ProgramOrderGateResult operate(List<String> keys, String[] args) {
        if (redisScript == null) {
            throw new IllegalStateException("programOrderGate lua script is not initialized");
        }
        Object object = redisCache.getInstance().execute(redisScript, keys, args);
        String result = (String) object;
        if (StringUtil.isEmpty(result)) {
            throw new IllegalStateException("programOrderGate lua script result is empty");
        }
        return JSON.parseObject(result, ProgramOrderGateResult.class);
    }
}
