package com.tikectsystem.service;

import cn.hutool.core.date.DateUtil;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tikectsystem.dto.DepthRuleDto;
import com.tikectsystem.dto.DepthRuleStatusDto;
import com.tikectsystem.dto.DepthRuleUpdateDto;
import com.tikectsystem.entity.DepthRule;
import com.tikectsystem.enums.BaseCode;
import com.tikectsystem.enums.RuleStatus;
import com.tikectsystem.exception.TikectsystemFrameException;
import com.tikectsystem.mapper.DepthRuleMapper;
import com.tikectsystem.util.DateUtils;
import com.tikectsystem.util.StringUtil;
import com.tikectsystem.vo.DepthRuleVo;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 深度规则 service
 * @author: 阿星不是程序员
 **/
@Service
public class DepthRuleService {

    @Autowired
    private DepthRuleMapper depthRuleMapper;

    @Autowired
    private RuleService ruleService;
    @Autowired
    private UidGenerator uidGenerator;

    @Transactional(rollbackFor = Exception.class)
    public void depthRuleAdd(DepthRuleDto depthRuleDto) {
        check(depthRuleDto.getStartTimeWindow(),depthRuleDto.getEndTimeWindow());
        add(depthRuleDto);
        ruleService.saveAllRuleCache();
    }
    
    public void check(String startTimeWindow, String endTimeWindow){
        if (StringUtil.isEmpty(startTimeWindow) || StringUtil.isEmpty(endTimeWindow)) {
            return;
        }
        long checkStartTimeWindowTimestamp = getTimeWindowTimestamp(startTimeWindow);
        long checkEndTimeWindowTimestamp = getTimeWindowTimestamp(endTimeWindow);
        checkTimeWindowRange(checkStartTimeWindowTimestamp,checkEndTimeWindowTimestamp);
        LambdaQueryWrapper<DepthRule> queryWrapper = Wrappers.lambdaQuery(DepthRule.class).eq(DepthRule::getStatus, RuleStatus.RUN.getCode());
        List<DepthRule> depthRules = depthRuleMapper.selectList(queryWrapper);
        for (final DepthRule depthRule : depthRules) {
            if (StringUtil.isEmpty(depthRule.getStartTimeWindow()) || StringUtil.isEmpty(depthRule.getEndTimeWindow())) {
                continue;
            }
            long startTimeWindowTimestamp = getTimeWindowTimestamp(depthRule.getStartTimeWindow());
            long endTimeWindowTimestamp = getTimeWindowTimestamp(depthRule.getEndTimeWindow());
            checkTimeWindowRange(startTimeWindowTimestamp,endTimeWindowTimestamp);
            if (checkTimeWindowIntersect(checkStartTimeWindowTimestamp,checkEndTimeWindowTimestamp,
                    startTimeWindowTimestamp,endTimeWindowTimestamp)) {
                throw new TikectsystemFrameException(BaseCode.API_RULE_TIME_WINDOW_INTERSECT);
            }
        }
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
    
    @Transactional(rollbackFor = Exception.class)
    public void add(DepthRuleDto depthRuleDto) {
        DepthRule depthRule = new DepthRule();
        BeanUtils.copyProperties(depthRuleDto,depthRule);
        depthRule.setId(uidGenerator.getUid());
        depthRule.setCreateTime(DateUtils.now());
        depthRuleMapper.insert(depthRule);
    }
    
    @Transactional(rollbackFor = Exception.class)
    public void depthRuleUpdate(final DepthRuleUpdateDto depthRuleUpdateDto) {
        update(depthRuleUpdateDto);
        ruleService.saveAllRuleCache();
    }
    
    @Transactional(rollbackFor = Exception.class)
    public void update(final DepthRuleUpdateDto depthRuleUpdateDto) {
        DepthRule depthRule = new DepthRule();
        BeanUtils.copyProperties(depthRuleUpdateDto,depthRule);
        depthRuleMapper.updateById(depthRule);
    }
    
    @Transactional(rollbackFor = Exception.class)
    public void depthRuleUpdateStatus(final DepthRuleStatusDto depthRuleStatusDto) {
        updateStatus(depthRuleStatusDto);
        ruleService.saveAllRuleCache();
    }
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(final DepthRuleStatusDto depthRuleStatusDto) {
        DepthRule depthRule = new DepthRule();
        depthRule.setId(depthRuleStatusDto.getId());
        depthRule.setStatus(depthRuleStatusDto.getStatus());
        depthRuleMapper.updateById(depthRule);
    }
    
    public List<DepthRuleVo> selectList() {
        List<DepthRule> depthRules = depthRuleMapper.selectList(null);
        List<DepthRuleVo> depthRuleVos = depthRules.stream().map(depthRule -> {
            DepthRuleVo depthRuleVo = new DepthRuleVo();
            BeanUtils.copyProperties(depthRule, depthRuleVo);
            return depthRuleVo;
        }).collect(Collectors.toList());
        return depthRuleVos;
    }
    
    @Transactional(rollbackFor = Exception.class)
    public void delAll(){
        depthRuleMapper.delAll();
    }
}
