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
import java.util.List;
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
    public OrderRequestResult saveProcessing(String requestId, Long orderNumber, Long programId, Long userId) {
        OrderRequestResult exists = getByRequestId(requestId);
        if (Objects.nonNull(exists)) {
            return exists;
        }
        exists = getByOrderNumber(orderNumber);
        if (Objects.nonNull(exists)) {
            return exists;
        }
        OrderRequestResult result = new OrderRequestResult();
        result.setId(uidGenerator.getUid());
        result.setRequestId(requestId);
        result.setOrderNumber(orderNumber);
        result.setProgramId(programId);
        result.setUserId(userId);
        result.setResultStatus(OrderRequestResultStatus.PROCESSING);
        orderRequestResultMapper.insert(result);
        return result;
    }

    /**
     * 记录 Redis 已最终锁座状态。
     * @param orderNumber 订单编号
     * @param reservationJson 锁座快照
     * @param expireTime 过期时间
     */
    public void markReserved(Long orderNumber, String reservationJson, Date expireTime) {
        OrderRequestResult current = getByOrderNumber(orderNumber);
        if (Objects.isNull(current) || Objects.equals(current.getResultStatus(), OrderRequestResultStatus.RESERVED) ||
                isTerminalStatus(current.getResultStatus())) {
            return;
        }
        checkTransition(current.getResultStatus(), OrderRequestResultStatus.RESERVED);
        OrderRequestResult update = new OrderRequestResult();
        update.setResultStatus(OrderRequestResultStatus.RESERVED);
        update.setReservationJson(reservationJson);
        update.setExpireTime(expireTime);
        int count = orderRequestResultMapper.update(update, Wrappers.lambdaUpdate(OrderRequestResult.class)
                .eq(OrderRequestResult::getOrderNumber, orderNumber)
                .eq(OrderRequestResult::getResultStatus, current.getResultStatus()));
        if (count <= 0) {
            throw new IllegalStateException("order request result reserved status update failed");
        }
    }

    /**
     * 记录失败状态。
     * @param orderNumber 订单编号
     * @param failCode 失败编码
     * @param failMessage 失败原因
     */
    public void markFailed(Long orderNumber, String failCode, String failMessage) {
        OrderRequestResult current = getByOrderNumber(orderNumber);
        if (Objects.isNull(current) || Objects.equals(current.getResultStatus(), OrderRequestResultStatus.FAILED) ||
                isTerminalStatus(current.getResultStatus())) {
            return;
        }
        checkTransition(current.getResultStatus(), OrderRequestResultStatus.FAILED);
        OrderRequestResult update = new OrderRequestResult();
        update.setResultStatus(OrderRequestResultStatus.FAILED);
        update.setFailCode(failCode);
        update.setFailMessage(failMessage);
        int count = orderRequestResultMapper.update(update, Wrappers.lambdaUpdate(OrderRequestResult.class)
                .eq(OrderRequestResult::getOrderNumber, orderNumber)
                .eq(OrderRequestResult::getResultStatus, current.getResultStatus()));
        if (count <= 0) {
            throw new IllegalStateException("order request result failed status update failed");
        }
    }

    /**
     * 内部服务回写状态。
     * @param orderRequestResultUpdateDto 状态更新参数
     * @return 是否更新成功
     */
    public boolean updateStatus(OrderRequestResultUpdateDto orderRequestResultUpdateDto) {
        checkTransition(orderRequestResultUpdateDto.getBeforeStatus(), orderRequestResultUpdateDto.getStatus());
        OrderRequestResult update = new OrderRequestResult();
        update.setResultStatus(orderRequestResultUpdateDto.getStatus());
        update.setFailCode(orderRequestResultUpdateDto.getFailCode());
        update.setFailMessage(orderRequestResultUpdateDto.getFailMessage());
        int updateCount = orderRequestResultMapper.update(update, Wrappers.lambdaUpdate(OrderRequestResult.class)
                .eq(OrderRequestResult::getOrderNumber, orderRequestResultUpdateDto.getOrderNumber())
                .eq(OrderRequestResult::getResultStatus, orderRequestResultUpdateDto.getBeforeStatus()));
        if (updateCount > 0) {
            return true;
        }
        OrderRequestResult current = getByOrderNumber(orderRequestResultUpdateDto.getOrderNumber());
        return Objects.nonNull(current) && Objects.equals(current.getResultStatus(), orderRequestResultUpdateDto.getStatus());
    }

    /**
     * 将长时间停留在 PROCESSING 的请求过期，避免请求表永久卡在中间态。
     * @param beforeTime 创建时间早于该时间的请求会被过期
     * @param limit 单次处理数量
     * @return 实际过期数量
     */
    public int expireStuckProcessing(Date beforeTime, int limit) {
        List<OrderRequestResult> resultList = orderRequestResultMapper.selectList(
                Wrappers.lambdaQuery(OrderRequestResult.class)
                        .select(OrderRequestResult::getOrderNumber)
                        .eq(OrderRequestResult::getResultStatus, OrderRequestResultStatus.PROCESSING)
                        .le(OrderRequestResult::getCreateTime, beforeTime)
                        .last("limit " + limit));
        int expireCount = 0;
        for (OrderRequestResult result : resultList) {
            checkTransition(OrderRequestResultStatus.PROCESSING, OrderRequestResultStatus.EXPIRED);
            OrderRequestResult update = new OrderRequestResult();
            update.setResultStatus(OrderRequestResultStatus.EXPIRED);
            update.setFailCode(OrderRequestResultStatus.EXPIRED);
            update.setFailMessage("下单请求处理超时");
            int updateCount = orderRequestResultMapper.update(update, Wrappers.lambdaUpdate(OrderRequestResult.class)
                    .eq(OrderRequestResult::getOrderNumber, result.getOrderNumber())
                    .eq(OrderRequestResult::getResultStatus, OrderRequestResultStatus.PROCESSING));
            if (updateCount > 0) {
                expireCount++;
            }
        }
        return expireCount;
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

    /**
     * 按请求幂等号查询下单请求结果。
     *
     * @param requestId 下单请求幂等号
     * @return 请求结果
     */
    public OrderRequestResult getByRequestId(String requestId) {
        if (StrUtil.isBlank(requestId)) {
            return null;
        }
        return orderRequestResultMapper.selectOne(Wrappers.lambdaQuery(OrderRequestResult.class)
                .eq(OrderRequestResult::getRequestId, requestId));
    }

    private void checkTransition(String beforeStatus, String afterStatus) {
        if (Objects.equals(beforeStatus, afterStatus)) {
            return;
        }
        boolean allowed = Objects.equals(beforeStatus, OrderRequestResultStatus.PROCESSING) &&
                (Objects.equals(afterStatus, OrderRequestResultStatus.RESERVED) ||
                        Objects.equals(afterStatus, OrderRequestResultStatus.ORDER_CREATED) ||
                        Objects.equals(afterStatus, OrderRequestResultStatus.FAILED) ||
                        Objects.equals(afterStatus, OrderRequestResultStatus.CANCELLED) ||
                        Objects.equals(afterStatus, OrderRequestResultStatus.EXPIRED));
        allowed = allowed || Objects.equals(beforeStatus, OrderRequestResultStatus.RESERVED) &&
                (Objects.equals(afterStatus, OrderRequestResultStatus.ORDER_CREATED) ||
                        Objects.equals(afterStatus, OrderRequestResultStatus.FAILED) ||
                        Objects.equals(afterStatus, OrderRequestResultStatus.CANCELLED) ||
                        Objects.equals(afterStatus, OrderRequestResultStatus.EXPIRED));
        allowed = allowed || Objects.equals(beforeStatus, OrderRequestResultStatus.ORDER_CREATED) &&
                Objects.equals(afterStatus, OrderRequestResultStatus.CANCELLED);
        if (!allowed) {
            throw new IllegalStateException("illegal order request result status transition");
        }
    }

    private boolean isTerminalStatus(String status) {
        return Objects.equals(status, OrderRequestResultStatus.ORDER_CREATED) ||
                Objects.equals(status, OrderRequestResultStatus.FAILED) ||
                Objects.equals(status, OrderRequestResultStatus.CANCELLED) ||
                Objects.equals(status, OrderRequestResultStatus.EXPIRED);
    }
}
