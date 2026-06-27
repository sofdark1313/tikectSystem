package com.tikectsystem.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baidu.fsg.uid.UidGenerator;
import com.tikectsystem.dto.OrderRequestResultQueryDto;
import com.tikectsystem.dto.OrderRequestResultUpdateDto;
import com.tikectsystem.entity.OrderRequestResult;
import com.tikectsystem.mapper.OrderRequestResultMapper;
import com.tikectsystem.service.constant.OrderRequestResultStatus;
import com.tikectsystem.vo.OrderRequestResultVo;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Objects;

/**
 * 异步下单请求结果服务。
 */
@Service
public class OrderRequestResultService {

    @Autowired
    private UidGenerator uidGenerator;

    @Autowired
    private OrderRequestResultMapper orderRequestResultMapper;

    /**
     * 记录 Kafka 已受理状态。
     * @param requestId 请求幂等号
     * @param orderNumber 订单编号
     * @param programId 节目编号
     * @param userId 用户编号
     */
    public void saveProcessing(String requestId, Long orderNumber, Long programId, Long userId) {
        OrderRequestResult exists = getByOrderNumber(orderNumber);
        if (Objects.nonNull(exists)) {
            return;
        }
        OrderRequestResult result = new OrderRequestResult();
        result.setId(uidGenerator.getUid());
        result.setRequestId(requestId);
        result.setOrderNumber(orderNumber);
        result.setProgramId(programId);
        result.setUserId(userId);
        result.setResultStatus(OrderRequestResultStatus.PROCESSING);
        orderRequestResultMapper.insert(result);
    }

    /**
     * 记录 Redis 已最终锁座状态。
     * @param orderNumber 订单编号
     * @param reservationJson 锁座快照
     * @param expireTime 过期时间
     */
    public void markReserved(Long orderNumber, String reservationJson, Date expireTime) {
        OrderRequestResult update = new OrderRequestResult();
        update.setResultStatus(OrderRequestResultStatus.RESERVED);
        update.setReservationJson(reservationJson);
        update.setExpireTime(expireTime);
        orderRequestResultMapper.update(update, Wrappers.lambdaUpdate(OrderRequestResult.class)
                .eq(OrderRequestResult::getOrderNumber, orderNumber));
    }

    /**
     * 记录失败状态。
     * @param orderNumber 订单编号
     * @param failCode 失败编码
     * @param failMessage 失败原因
     */
    public void markFailed(Long orderNumber, String failCode, String failMessage) {
        OrderRequestResult update = new OrderRequestResult();
        update.setResultStatus(OrderRequestResultStatus.FAILED);
        update.setFailCode(failCode);
        update.setFailMessage(failMessage);
        orderRequestResultMapper.update(update, Wrappers.lambdaUpdate(OrderRequestResult.class)
                .eq(OrderRequestResult::getOrderNumber, orderNumber));
    }

    /**
     * 内部服务回写状态。
     * @param orderRequestResultUpdateDto 状态更新参数
     * @return 是否更新成功
     */
    public boolean updateStatus(OrderRequestResultUpdateDto orderRequestResultUpdateDto) {
        OrderRequestResult update = new OrderRequestResult();
        update.setResultStatus(orderRequestResultUpdateDto.getStatus());
        update.setFailCode(orderRequestResultUpdateDto.getFailCode());
        update.setFailMessage(orderRequestResultUpdateDto.getFailMessage());
        return orderRequestResultMapper.update(update, Wrappers.lambdaUpdate(OrderRequestResult.class)
                .eq(OrderRequestResult::getOrderNumber, orderRequestResultUpdateDto.getOrderNumber())) > 0;
    }

    /**
     * 查询下单请求结果。
     * @param orderRequestResultQueryDto 查询参数
     * @return 请求结果视图
     */
    public OrderRequestResultVo get(OrderRequestResultQueryDto orderRequestResultQueryDto) {
        OrderRequestResult result = null;
        if (Objects.nonNull(orderRequestResultQueryDto.getOrderNumber())) {
            result = getByOrderNumber(orderRequestResultQueryDto.getOrderNumber());
        } else if (StrUtil.isNotBlank(orderRequestResultQueryDto.getRequestId())) {
            result = orderRequestResultMapper.selectOne(Wrappers.lambdaQuery(OrderRequestResult.class)
                    .eq(OrderRequestResult::getRequestId, orderRequestResultQueryDto.getRequestId()));
        }
        if (Objects.isNull(result)) {
            return null;
        }
        OrderRequestResultVo orderRequestResultVo = new OrderRequestResultVo();
        BeanUtils.copyProperties(result, orderRequestResultVo);
        orderRequestResultVo.setStatus(result.getResultStatus());
        return orderRequestResultVo;
    }

    /**
     * 按订单编号查询请求结果。
     * @param orderNumber 订单编号
     * @return 请求结果
     */
    public OrderRequestResult getByOrderNumber(Long orderNumber) {
        return orderRequestResultMapper.selectOne(Wrappers.lambdaQuery(OrderRequestResult.class)
                .eq(OrderRequestResult::getOrderNumber, orderNumber));
    }
}
