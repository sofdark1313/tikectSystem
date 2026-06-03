package com.tikectsystem.context;

import com.tikectsystem.core.DelayProduceQueue;
import com.tikectsystem.core.IsolationRegionSelector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 延迟队列 发送者 分片选择
 * @author: 阿星不是程序员
 **/
public class DelayQueueProduceCombine {
    
    private final IsolationRegionSelector isolationRegionSelector;
    
    private final List<DelayProduceQueue> delayProduceQueueList = new ArrayList<>();
    
    public DelayQueueProduceCombine(DelayQueueBasePart delayQueueBasePart,String topic){
        //消息的分区数
        Integer isolationRegionCount = delayQueueBasePart.getDelayQueueProperties().getIsolationRegionCount();
        //根据分区数来构建分区选择器
        isolationRegionSelector =new IsolationRegionSelector(isolationRegionCount);
        for(int i = 0; i < isolationRegionCount; i++) {
            //按照分区数来构建发送者延迟队列，然后放到存储里
            delayProduceQueueList.add(new DelayProduceQueue(delayQueueBasePart.getRedissonClient(),topic + "-" + i));
        }
    }
    
    public void offer(String content,long delayTime, TimeUnit timeUnit){
        int index = isolationRegionSelector.getIndex();
        //拿取分片
        delayProduceQueueList.get(index).offer(content, delayTime, timeUnit);
    }
}
