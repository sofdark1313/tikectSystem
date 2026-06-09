package com.tikectsystem.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import com.tikectsystem.util.StringUtil;
import com.tikectsystem.dto.AllRuleDto;
import com.tikectsystem.dto.DepthRuleDto;
import com.tikectsystem.enums.BaseCode;
import com.tikectsystem.enums.RuleStatus;
import com.tikectsystem.exception.TikectsystemFrameException;
import com.tikectsystem.vo.AllDepthRuleVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 所有规则 service
 * @author: 阿星不是程序员
 **/
@Service
public class AllRuleService {

    @Autowired
    private RuleService ruleService;

    @Autowired
    private DepthRuleService depthRuleService;
    
    @Transactional(rollbackFor = Exception.class)
    public void add(final AllRuleDto allRuleDto) {
        ruleService.add(allRuleDto.getRuleDto());
        depthRuleService.delAll();
        List<DepthRuleDto> depthRuleDtoList = allRuleDto.getDepthRuleDtoList();
        if (CollUtil.isNotEmpty(depthRuleDtoList)) {
            for (int i = 0; i < depthRuleDtoList.size(); i++) {
                DepthRuleDto depthRuleDto = depthRuleDtoList.get(i);
                checkTime(depthRuleDto.getStartTimeWindow(),depthRuleDto.getEndTimeWindow(),filterDepthRuleDtoList(depthRuleDtoList,i));
                depthRuleService.add(depthRuleDto);
            }
        }
        ruleService.saveAllRuleCache();
    }
    
    public void checkTime(String startTimeWindow, String endTimeWindow, List<DepthRuleDto> depthRuleDtoList){
        if (StringUtil.isEmpty(startTimeWindow) || StringUtil.isEmpty(endTimeWindow)) {
            return;
        }
        long checkStartTimeWindowTimestamp = getTimeWindowTimestamp(startTimeWindow);
        long checkEndTimeWindowTimestamp = getTimeWindowTimestamp(endTimeWindow);
        checkTimeWindowRange(checkStartTimeWindowTimestamp,checkEndTimeWindowTimestamp);
        depthRuleDtoList = depthRuleDtoList.stream().filter(depthRuleDto -> {
            if (depthRuleDto.getStatus() != null) {
                if (RuleStatus.RUN.getCode().equals(depthRuleDto.getStatus())) {
                    return true;
                }else {
                    return false;
                }
            }else {
                return true;
            }
        }).collect(Collectors.toList());
        for (final DepthRuleDto depthRuleDto : depthRuleDtoList) {
            if (StringUtil.isEmpty(depthRuleDto.getStartTimeWindow()) || StringUtil.isEmpty(depthRuleDto.getEndTimeWindow())) {
                continue;
            }
            long startTimeWindowTimestamp = getTimeWindowTimestamp(depthRuleDto.getStartTimeWindow());
            long endTimeWindowTimestamp = getTimeWindowTimestamp(depthRuleDto.getEndTimeWindow());
            checkTimeWindowRange(startTimeWindowTimestamp,endTimeWindowTimestamp);
            if (checkTimeWindowIntersect(checkStartTimeWindowTimestamp,checkEndTimeWindowTimestamp,
                    startTimeWindowTimestamp,endTimeWindowTimestamp)) {
                throw new TikectsystemFrameException(BaseCode.API_RULE_TIME_WINDOW_INTERSECT);
            }
        }
    }
    
    public List<DepthRuleDto> filterDepthRuleDtoList(List<DepthRuleDto> depthRuleDtoList, int coord){
        List<DepthRuleDto> fiterDepthRuleDtoList = new ArrayList<>();
        for (int i = 0; i < depthRuleDtoList.size(); i++) {
            if (i != coord) {
                fiterDepthRuleDtoList.add(depthRuleDtoList.get(i));
            }
        }
        return fiterDepthRuleDtoList;
    }
    public long getTimeWindowTimestamp(String timeWindow){
        String today = DateUtil.today();
        try {
            return DateUtil.parse(today + " " + timeWindow).getTime();
        } catch (RuntimeException e) {
            throw new TikectsystemFrameException(BaseCode.PARAMETER_ERROR);
        }
    }

    private void checkTimeWindowRange(long startTimeWindowTimestamp, long endTimeWindowTimestamp) {
        if (endTimeWindowTimestamp < startTimeWindowTimestamp) {
            throw new TikectsystemFrameException(BaseCode.PARAMETER_ERROR);
        }
    }

    private boolean checkTimeWindowIntersect(long checkStartTimeWindowTimestamp, long checkEndTimeWindowTimestamp,
                                             long startTimeWindowTimestamp, long endTimeWindowTimestamp) {
        return checkStartTimeWindowTimestamp <= endTimeWindowTimestamp && checkEndTimeWindowTimestamp >= startTimeWindowTimestamp;
    }
    
    public AllDepthRuleVo get() {
        AllDepthRuleVo allDepthRuleVo = new AllDepthRuleVo();
        allDepthRuleVo.setRuleVo(ruleService.get());
        allDepthRuleVo.setDepthRuleVoList(depthRuleService.selectList());
        return allDepthRuleVo;
    }
}
