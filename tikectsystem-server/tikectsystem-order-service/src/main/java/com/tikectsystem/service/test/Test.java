package com.tikectsystem.service.test;

import com.alibaba.fastjson2.JSON;
import com.tikectsystem.core.ConsumerTask;
import com.tikectsystem.dto.TestSendDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: Test
 * @author: 阿星不是程序员
 **/
@Slf4j
@Service
public class Test implements ConsumerTask {
    
    
    @Override
    public void execute(String content) {
        if (content == null) {
            log.error("test message is null");
            return;
        }
        TestSendDto testSendDto;
        try {
            testSendDto = JSON.parseObject(content, TestSendDto.class);
        } catch (Exception e) {
            log.error("test message parse error, content : {}", content, e);
            return;
        }
        if (testSendDto == null || testSendDto.getTime() == null) {
            log.error("test message data invalid, content : {}", content);
            return;
        }
        log.info("收到消息 : {} 延时: {} 毫秒" ,content,System.currentTimeMillis() - testSendDto.getTime() - 5000);
    }
    
    @Override
    public String topic() {
        return "test-topic";
    }
}
