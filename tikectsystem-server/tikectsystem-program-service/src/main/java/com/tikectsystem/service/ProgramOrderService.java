package com.tikectsystem.service;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baidu.fsg.uid.UidGenerator;
import com.tikectsystem.client.OrderClient;
import com.tikectsystem.common.ApiResponse;
import com.tikectsystem.core.RedisKeyManage;
import com.tikectsystem.domain.OrderCreateMq;
import com.tikectsystem.domain.PurchaseSeat;
import com.tikectsystem.dto.DelayOrderCancelDto;
import com.tikectsystem.dto.OrderCreateDto;
import com.tikectsystem.dto.OrderGetDto;
import com.tikectsystem.dto.OrderRequestResultUpdateDto;
import com.tikectsystem.dto.OrderTicketUserCreateDto;
import com.tikectsystem.dto.ProgramOrderCreateDto;
import com.tikectsystem.dto.SeatDto;
import com.tikectsystem.entity.OrderRequestResult;
import com.tikectsystem.entity.ProgramShowTime;
import com.tikectsystem.entity.ProgramRecordTask;
import com.tikectsystem.enums.BaseCode;
import com.tikectsystem.enums.CompositeCheckType;
import com.tikectsystem.enums.OrderStatus;
import com.tikectsystem.enums.RecordType;
import com.tikectsystem.enums.SellStatus;
import com.tikectsystem.exception.TikectsystemFrameException;
import com.tikectsystem.initialize.impl.composite.CompositeContainer;
import com.tikectsystem.mapper.ProgramRecordTaskMapper;
import com.tikectsystem.redis.RedisKeyBuild;
import com.tikectsystem.service.delaysend.DelayOrderCancelSend;
import com.tikectsystem.service.domain.CreateOrderTemporaryData;
import com.tikectsystem.service.executor.ProgramRecordTaskExecutor;
import com.tikectsystem.service.kafka.CreateOrderMqDomain;
import com.tikectsystem.service.kafka.CreateOrderSend;
import com.tikectsystem.service.kafka.OrderKafkaSend;
import com.tikectsystem.service.kafka.OrderRequestMq;
import com.tikectsystem.service.constant.OrderRequestResultStatus;
import com.tikectsystem.service.lua.ProgramCacheCreateOrderData;
import com.tikectsystem.service.lua.ProgramCacheCreateOrderResolutionOperate;
import com.tikectsystem.service.lua.ProgramCacheResolutionOperate;
import com.tikectsystem.service.lua.ProgramOrderGateOperate;
import com.tikectsystem.service.lua.ProgramOrderGateResult;
import com.tikectsystem.service.tool.SeatMatch;
import com.tikectsystem.util.DateUtils;
import com.tikectsystem.util.StringUtil;
import com.tikectsystem.vo.OrderGetVo;
import com.tikectsystem.vo.ProgramVo;
import com.tikectsystem.vo.SeatVo;
import com.tikectsystem.vo.TicketCategoryVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.tikectsystem.constant.Constant.GLIDE_LINE;
import static com.tikectsystem.constant.ProgramOrderConstant.DELAY_ORDER_CANCEL_TIME;
import static com.tikectsystem.constant.ProgramOrderConstant.DELAY_ORDER_CANCEL_TIME_UNIT;
import static com.tikectsystem.constant.ProgramOrderConstant.ORDER_TABLE_COUNT;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 节目订单 service
 * @author: 阿星不是程序员
 **/
@Slf4j
@Service
public class ProgramOrderService {

    private static final long KAFKA_ACK_TIMEOUT_SECONDS = 5L;

    private static final long REQUEST_IDEMPOTENT_LOCK_TTL_SECONDS = 30L;

    private static final int REQUEST_IDEMPOTENT_WAIT_TIMES = 100;

    private static final long REQUEST_IDEMPOTENT_WAIT_MILLIS = 50L;

    @Autowired
    private OrderClient orderClient;

    @Autowired
    private UidGenerator uidGenerator;

    @Autowired
    private ProgramCacheResolutionOperate programCacheResolutionOperate;

    @Autowired
    ProgramCacheCreateOrderResolutionOperate programCacheCreateOrderResolutionOperate;

    @Autowired
    private DelayOrderCancelSend delayOrderCancelSend;

    @Autowired
    private CreateOrderSend createOrderSend;

    @Autowired
    private OrderKafkaSend orderKafkaSend;

    @Autowired
    private ProgramOrderGateOperate programOrderGateOperate;

    @Autowired
    private com.tikectsystem.redis.RedisCache redisCache;

    @Autowired
    private ProgramService programService;

    @Autowired
    private ProgramShowTimeService programShowTimeService;

    @Autowired
    private TicketCategoryService ticketCategoryService;

    @Autowired
    private SeatService seatService;

    @Autowired
    private ProgramRecordTaskMapper programRecordTaskMapper;

    @Autowired
    private ProgramRecordTaskExecutor programRecordTaskExecutor;

    @Autowired
    private ProgramOrderCircuitBreakerService programOrderCircuitBreakerService;

    @Autowired
    private OrderRequestResultService orderRequestResultService;

    @Autowired
    private CompositeContainer compositeContainer;

    /**
     * V4 新链路入口：轻量准入成功并拿到 order_request Kafka ack 后返回订单编号。
     * 该入口不访问结果表，结果表首写由 order_request 消费端异步完成。
     * @param programOrderCreateDto 下单参数
     * @param orderVersion 下单版本
     * @return 订单编号
     */
    public String acceptOrderRequest(ProgramOrderCreateDto programOrderCreateDto, Integer orderVersion) {
        String requestId = ensureOrderRequestId(programOrderCreateDto);
        ProgramOrderCircuitBreakerService.CircuitAccessToken circuitToken =
                programOrderCircuitBreakerService.beforeRedisAccess(programOrderCreateDto);
        boolean idempotentLockAcquired;
        try {
            idempotentLockAcquired = acquireRequestIdempotentLock(requestId);
        } catch (RuntimeException e) {
            throw handleRedisInfrastructureFailure(circuitToken, e);
        }
        if (!idempotentLockAcquired) {
            try {
                String acceptedOrderNumber = waitForAcceptedOrderNumber(requestId);
                programOrderCircuitBreakerService.afterRedisSuccess(circuitToken);
                if (!StringUtil.isEmpty(acceptedOrderNumber)) {
                    return acceptedOrderNumber;
                }
            } catch (RuntimeException e) {
                if (isRedisInfrastructureException(e)) {
                    throw handleRedisInfrastructureFailure(circuitToken, e);
                }
                programOrderCircuitBreakerService.afterRedisSuccess(circuitToken);
                throw e;
            }
            throw new TikectsystemFrameException(BaseCode.OPERATION_IS_TOO_FREQUENT_PLEASE_TRY_AGAIN_LATER);
        }
        try {
            executeProgramOrderCreateCheck(programOrderCreateDto);
            prepareCreateOrderProgramCache(programOrderCreateDto);
        } catch (RuntimeException e) {
            try {
                if (isRedisInfrastructureException(e)) {
                    throw handleRedisInfrastructureFailure(circuitToken, e);
                } else {
                    programOrderCircuitBreakerService.afterRedisSuccess(circuitToken);
                    throw e;
                }
            } finally {
                releaseRequestIdempotentLockSafely(requestId, circuitToken);
            }
        }
        return sendAcceptedOrderRequest(programOrderCreateDto, orderVersion, requestId, circuitToken);
    }

    private void executeProgramOrderCreateCheck(ProgramOrderCreateDto programOrderCreateDto) {
        compositeContainer.execute(CompositeCheckType.PROGRAM_ORDER_CREATE_CHECK.getValue(), programOrderCreateDto);
    }

    private String sendAcceptedOrderRequest(ProgramOrderCreateDto programOrderCreateDto, Integer orderVersion,
                                            String requestId,
                                            ProgramOrderCircuitBreakerService.CircuitAccessToken circuitToken) {
        try {
            Long orderNumber;
            ProgramOrderGateResult gateResult;
            try {
                orderNumber = uidGenerator.getOrderNumber(programOrderCreateDto.getUserId(), ORDER_TABLE_COUNT);
            } catch (RuntimeException e) {
                programOrderCircuitBreakerService.afterRedisSuccess(circuitToken);
                throw e;
            }
            try {
                gateResult = gateOrderRequest(programOrderCreateDto, requestId, orderNumber);
                programOrderCircuitBreakerService.afterRedisSuccess(circuitToken);
            } catch (RuntimeException e) {
                throw handleRedisInfrastructureFailure(circuitToken, e);
            }
            if (!Objects.equals(gateResult.getCode(), BaseCode.SUCCESS.getCode())) {
                BaseCode baseCode = BaseCode.getRc(gateResult.getCode());
                BaseCode failureCode = baseCode == null ? BaseCode.SYSTEM_ERROR : baseCode;
                throw new TikectsystemFrameException(failureCode);
            }
            if (!StringUtil.isEmpty(gateResult.getOrderNumber())) {
                Long acceptedOrderNumber = Long.valueOf(gateResult.getOrderNumber());
                if (!Objects.equals(acceptedOrderNumber, orderNumber)) {
                    return String.valueOf(acceptedOrderNumber);
                }
                orderNumber = acceptedOrderNumber;
            }
            OrderRequestMq orderRequestMq = new OrderRequestMq();
            orderRequestMq.setRequestId(requestId);
            orderRequestMq.setOrderNumber(orderNumber);
            orderRequestMq.setProgramOrderCreateDto(programOrderCreateDto);
            orderRequestMq.setOrderVersion(orderVersion);
            orderRequestMq.setCreateTime(DateUtils.now());

            CountDownLatch latch = new CountDownLatch(1);
            CreateOrderMqDomain createOrderMqDomain = new CreateOrderMqDomain();
            createOrderMqDomain.orderNumber = String.valueOf(orderNumber);
            Long finalOrderNumber = orderNumber;
            String kafkaRequestId = requestId;
            try {
                orderKafkaSend.sendOrderRequest(String.valueOf(orderNumber), JSON.toJSONString(orderRequestMq), sendResult -> {
                    try {
                        log.debug("order_request kafka send success, orderNumber : {}", finalOrderNumber);
                    } catch (Exception e) {
                        createOrderMqDomain.tikectsystemFrameException = new TikectsystemFrameException(e);
                    } finally {
                        latch.countDown();
                    }
                }, ex -> {
                    try {
                        releaseGateSafely(programOrderCreateDto, kafkaRequestId);
                        createOrderMqDomain.tikectsystemFrameException = new TikectsystemFrameException(ex);
                    } catch (Exception e) {
                        createOrderMqDomain.tikectsystemFrameException = new TikectsystemFrameException(e);
                    } finally {
                        latch.countDown();
                    }
                });
            } catch (RuntimeException e) {
                releaseGateSafely(programOrderCreateDto, requestId);
                throw e;
            }
            if (!awaitKafkaAck(latch)) {
                log.warn("order_request kafka ack timeout, return accepted orderNumber for polling, orderNumber : {}",
                        orderNumber);
                return String.valueOf(orderNumber);
            }
            if (Objects.nonNull(createOrderMqDomain.tikectsystemFrameException)) {
                throw createOrderMqDomain.tikectsystemFrameException;
            }
            return String.valueOf(orderNumber);
        } finally {
            releaseRequestIdempotentLockSafely(requestId, circuitToken);
        }
    }

    private String ensureOrderRequestId(ProgramOrderCreateDto programOrderCreateDto) {
        String requestId = StringUtil.isEmpty(programOrderCreateDto.getRequestId()) ?
                String.valueOf(uidGenerator.getUid()) : programOrderCreateDto.getRequestId();
        programOrderCreateDto.setRequestId(requestId);
        return requestId;
    }

    private boolean acquireRequestIdempotentLock(String requestId) {
        return redisCache.setIfAbsent(RedisKeyBuild.createRedisKey(
                        RedisKeyManage.PROGRAM_ORDER_REQUEST_IDEMPOTENT, requestId), requestId,
                REQUEST_IDEMPOTENT_LOCK_TTL_SECONDS, TimeUnit.SECONDS);
    }

    private String waitForAcceptedOrderNumber(String requestId) {
        for (int i = 0; i < REQUEST_IDEMPOTENT_WAIT_TIMES; i++) {
            String orderNumber = getAcceptedOrderNumber(requestId);
            if (!StringUtil.isEmpty(orderNumber)) {
                return orderNumber;
            }
            try {
                Thread.sleep(REQUEST_IDEMPOTENT_WAIT_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TikectsystemFrameException(e);
            }
        }
        return getAcceptedOrderNumber(requestId);
    }

    private String getAcceptedOrderNumber(String requestId) {
        return redisCache.get(RedisKeyBuild.createRedisKey(
                RedisKeyManage.PROGRAM_ORDER_GATE_REQUEST, requestId), String.class);
    }

    private void releaseRequestIdempotentLock(String requestId) {
        redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_ORDER_REQUEST_IDEMPOTENT, requestId));
    }

    private void releaseRequestIdempotentLockSafely(String requestId,
                                                    ProgramOrderCircuitBreakerService.CircuitAccessToken circuitToken) {
        try {
            releaseRequestIdempotentLock(requestId);
        } catch (RuntimeException e) {
            programOrderCircuitBreakerService.afterRedisFailure(circuitToken, e);
            log.warn("release request idempotent lock failed, requestId : {}", requestId, e);
        }
    }

    private TikectsystemFrameException handleRedisInfrastructureFailure(
            ProgramOrderCircuitBreakerService.CircuitAccessToken circuitToken,
            RuntimeException exception) {
        programOrderCircuitBreakerService.afterRedisFailure(circuitToken, exception);
        log.warn("program order redis infrastructure failure", exception);
        return new TikectsystemFrameException(BaseCode.PROGRAM_ORDER_CIRCUIT_OPEN);
    }

    /**
     * 消费 order_request 后执行最终锁座，并可靠投递 order_create。
     * @param orderRequestMq 下单受理消息
     */
    public void reserveAndSendOrderCreate(OrderRequestMq orderRequestMq) {
        OrderCreateMq orderCreateMq = reserveOrderRequest(orderRequestMq);
        if (Objects.isNull(orderCreateMq)) {
            return;
        }
        sendReservedOrderCreate(orderCreateMq);
    }

    /**
     * 执行 Redis Lua 最终锁座，成功返回可补发的 order_create 消息。
     * order_request 的 offset 应以本方法成功作为确认边界。
     */
    public OrderCreateMq reserveOrderRequest(OrderRequestMq orderRequestMq) {
        if (orderRequestMq == null || orderRequestMq.getOrderNumber() == null ||
                orderRequestMq.getProgramOrderCreateDto() == null) {
            return null;
        }
        ProgramOrderCreateDto programOrderCreateDto = orderRequestMq.getProgramOrderCreateDto();
        OrderRequestResult currentOrderRequestResult =
                orderRequestResultService.ensureProcessing(orderRequestMq.getRequestId(), orderRequestMq.getOrderNumber(),
                        programOrderCreateDto.getProgramId(), programOrderCreateDto.getUserId());
        if (Objects.nonNull(currentOrderRequestResult)) {
            String currentStatus = currentOrderRequestResult.getResultStatus();
            if (isTerminalOrderRequestStatus(currentStatus)) {
                log.warn("skip terminal order request, orderNumber : {}, status : {}",
                        orderRequestMq.getOrderNumber(), currentStatus);
                if (!Objects.equals(currentStatus, OrderRequestResultStatus.ORDER_CREATED)) {
                    if (noPayOrderExists(orderRequestMq.getOrderNumber())) {
                        markOrderRequestCreatedSafely(orderRequestMq.getOrderNumber());
                        return null;
                    }
                }
                return null;
            }
            if (Objects.equals(currentStatus, OrderRequestResultStatus.RESERVED)) {
                if (Objects.nonNull(currentOrderRequestResult.getExpireTime()) &&
                        currentOrderRequestResult.getExpireTime().before(DateUtils.now())) {
                    if (!expireReservedOrderRequestSafely(orderRequestMq, currentOrderRequestResult)) {
                        throw new IllegalStateException("expire reserved order request failed");
                    }
                    releaseGateSafely(programOrderCreateDto, orderRequestMq.getRequestId());
                    return null;
                }
                OrderCreateMq reservedOrderCreateMq = buildReservedOrderCreateMq(orderRequestMq, currentOrderRequestResult);
                if (Objects.nonNull(reservedOrderCreateMq)) {
                    releaseGateSafely(programOrderCreateDto, orderRequestMq.getRequestId());
                    return reservedOrderCreateMq;
                }
                if (!expireReservedOrderRequestSafely(orderRequestMq, currentOrderRequestResult)) {
                    throw new IllegalStateException("expire reserved order request failed");
                }
                releaseGateSafely(programOrderCreateDto, orderRequestMq.getRequestId());
                return null;
            }
        }
        ProgramOrderCircuitBreakerService.CircuitAccessToken circuitToken =
                programOrderCircuitBreakerService.beforeRedisAccess(programOrderCreateDto);
        CreateOrderTemporaryData createOrderTemporaryData;
        try {
            createOrderTemporaryData = createOrderOperateProgramCache(programOrderCreateDto, orderRequestMq.getOrderNumber());
            programOrderCircuitBreakerService.afterRedisSuccess(circuitToken);
        } catch (RuntimeException e) {
            boolean redisInfrastructureException = isRedisInfrastructureException(e);
            if (redisInfrastructureException) {
                programOrderCircuitBreakerService.afterRedisFailure(circuitToken, e);
            } else {
                programOrderCircuitBreakerService.afterRedisSuccess(circuitToken);
                markOrderRequestFailedSafely(orderRequestMq.getOrderNumber(), e);
            }
            releaseGateSafely(programOrderCreateDto, orderRequestMq.getRequestId());
            throw e;
        }
        String reservedStatus;
        try {
            reservedStatus = markOrderRequestReserved(orderRequestMq, createOrderTemporaryData);
        } catch (RuntimeException e) {
            releaseGateSafely(programOrderCreateDto, orderRequestMq.getRequestId());
            releaseReservation(orderRequestMq.getOrderNumber(), programOrderCreateDto.getProgramId(),
                    createOrderTemporaryData.getPurchaseSeatList());
            throw e;
        }
        releaseGateSafely(programOrderCreateDto, orderRequestMq.getRequestId());
        if (!Objects.equals(reservedStatus, OrderRequestResultStatus.RESERVED)) {
            if (Objects.equals(reservedStatus, OrderRequestResultStatus.ORDER_CREATED)) {
                return null;
            }
            releaseReservation(orderRequestMq.getOrderNumber(), programOrderCreateDto.getProgramId(),
                    createOrderTemporaryData.getPurchaseSeatList());
            if (isTerminalOrderRequestStatus(reservedStatus)) {
                log.warn("skip reserved order_create because request is terminal, orderNumber : {}, status : {}",
                        orderRequestMq.getOrderNumber(), reservedStatus);
                return null;
            }
            throw new TikectsystemFrameException(BaseCode.SYSTEM_ERROR);
        }
        try {
            OrderCreateDto orderCreateDto = buildCreateOrderParamV2(programOrderCreateDto.getProgramId(),
                    programOrderCreateDto.getUserId(), createOrderTemporaryData.getPurchaseSeatList(),
                    orderRequestMq.getOrderVersion(), orderRequestMq.getOrderNumber());
            return buildOrderCreateMq(orderCreateDto, createOrderTemporaryData.getIdentifierId());
        } catch (RuntimeException e) {
            markOrderRequestFailedSafely(orderRequestMq.getOrderNumber(), e);
            releaseReservation(orderRequestMq.getOrderNumber(), programOrderCreateDto.getProgramId(),
                    createOrderTemporaryData.getPurchaseSeatList());
            if (e instanceof TikectsystemFrameException) {
                throw e;
            }
            throw new TikectsystemFrameException(BaseCode.SYSTEM_ERROR.getCode(), BaseCode.SYSTEM_ERROR.getMsg(), e);
        }
    }

    /**
     * Redis 已完成锁座后投递 order_create。投递失败不释放锁座，交给 Kafka 重试或恢复扫描补发。
     */
    public void sendReservedOrderCreate(OrderCreateMq orderCreateMq) {
        sendOrderCreateByMq(orderCreateMq);
        try {
            programRecordTaskExecutor.execute(() -> createProgramRecordTask(orderCreateMq.getProgramId()));
        } catch (RuntimeException e) {
            log.warn("submit program record task failed, programId : {}", orderCreateMq.getProgramId(), e);
        }
    }

    /**
     * Redis 故障恢复时回扫 order_request 使用。
     * 订单已存在则标记完成；已锁座的请求优先使用结果表快照补发，避免重新锁座造成重复扣减。
     */
    public boolean recoverOrderRequest(OrderRequestMq orderRequestMq) {
        if (orderRequestMq == null || orderRequestMq.getOrderNumber() == null ||
                orderRequestMq.getProgramOrderCreateDto() == null) {
            return false;
        }
        if (noPayOrderExists(orderRequestMq.getOrderNumber())) {
            markOrderRequestCreatedSafely(orderRequestMq.getOrderNumber());
            return false;
        }
        if (orderExists(orderRequestMq.getOrderNumber())) {
            return false;
        }
        OrderRequestResult orderRequestResult = orderRequestResultService.getByOrderNumber(orderRequestMq.getOrderNumber());
        if (Objects.nonNull(orderRequestResult) &&
                Objects.equals(orderRequestResult.getResultStatus(), OrderRequestResultStatus.RESERVED)) {
            if (Objects.nonNull(orderRequestResult.getExpireTime()) &&
                    orderRequestResult.getExpireTime().before(DateUtils.now())) {
                if (!expireReservedOrderRequestSafely(orderRequestMq, orderRequestResult)) {
                    throw new IllegalStateException("expire reserved order request recovery failed");
                }
                log.warn("skip expired reserved order request recovery, orderNumber : {}",
                        orderRequestMq.getOrderNumber());
                return false;
            }
            OrderCreateMq reservedOrderCreateMq = buildReservedOrderCreateMq(orderRequestMq, orderRequestResult);
            if (Objects.nonNull(reservedOrderCreateMq)) {
                sendReservedOrderCreate(reservedOrderCreateMq);
                return true;
            }
        }
        OrderCreateMq orderCreateMq = reserveOrderRequest(orderRequestMq);
        if (Objects.isNull(orderCreateMq)) {
            return false;
        }
        sendReservedOrderCreate(orderCreateMq);
        return true;
    }

    /**
     * 根据结果表或 Redis reservation 中的锁座快照重建 order_create 消息。
     */
    private OrderCreateMq buildReservedOrderCreateMq(OrderRequestMq orderRequestMq,
                                                     OrderRequestResult orderRequestResult) {
        String reservationJson = getReservationJson(orderRequestMq, orderRequestResult);
        if (StringUtil.isEmpty(reservationJson)) {
            return null;
        }
        Long identifierId;
        List<PurchaseSeat> purchaseSeatList;
        try {
            JSONObject reservation = JSON.parseObject(reservationJson);
            identifierId = reservation.getLong("identifierId");
            JSONArray purchaseSeatArray = reservation.getJSONArray("purchaseSeatList");
            if (Objects.isNull(identifierId) || CollectionUtil.isEmpty(purchaseSeatArray)) {
                return null;
            }
            purchaseSeatList = purchaseSeatArray.toJavaList(PurchaseSeat.class);
        } catch (RuntimeException e) {
            log.warn("parse reserved order snapshot failed, orderNumber : {}",
                    orderRequestMq.getOrderNumber(), e);
            return null;
        }
        try {
            ProgramOrderCreateDto programOrderCreateDto = orderRequestMq.getProgramOrderCreateDto();
            rebindTicketUserIds(programOrderCreateDto, purchaseSeatList);
            validatePurchaseSeats(programOrderCreateDto, purchaseSeatList);
            OrderCreateDto orderCreateDto = buildCreateOrderParamV2(programOrderCreateDto.getProgramId(),
                    programOrderCreateDto.getUserId(), purchaseSeatList, orderRequestMq.getOrderVersion(),
                    orderRequestMq.getOrderNumber());
            return buildOrderCreateMq(orderCreateDto, identifierId);
        } catch (RuntimeException e) {
            if (isRedisInfrastructureException(e)) {
                throw e;
            }
            log.warn("build reserved order_create mq failed, orderNumber : {}",
                    orderRequestMq.getOrderNumber(), e);
            return null;
        }
    }

    private String getReservationJson(OrderRequestMq orderRequestMq, OrderRequestResult orderRequestResult) {
        // 结果表快照是补发的主事实来源，Redis reservation 只作为旧数据或异常窗口的兜底。
        String reservationJson = orderRequestResult.getReservationJson();
        if (!StringUtil.isEmpty(reservationJson) || Objects.isNull(orderRequestMq.getOrderNumber())) {
            return reservationJson;
        }
        return redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_ORDER_RESERVATION,
                orderRequestMq.getOrderNumber()), String.class);
    }

    /**
     * 已锁座但长期未建单时释放座位，并将请求置为 EXPIRED。
     */
    private boolean expireReservedOrderRequestSafely(OrderRequestMq orderRequestMq, OrderRequestResult orderRequestResult) {
        try {
            List<PurchaseSeat> purchaseSeatList = getReservationPurchaseSeatList(orderRequestResult.getReservationJson());
            if (CollectionUtil.isEmpty(purchaseSeatList)) {
                purchaseSeatList = buildReleaseFallbackPurchaseSeatList(orderRequestMq.getProgramOrderCreateDto());
            }
            releaseReservation(orderRequestMq.getOrderNumber(), orderRequestMq.getProgramOrderCreateDto().getProgramId(),
                    purchaseSeatList);
        } catch (RuntimeException e) {
            log.warn("release expired reserved order request failed, orderNumber : {}",
                    orderRequestMq.getOrderNumber(), e);
            return false;
        }
        try {
            OrderRequestResultUpdateDto updateDto = new OrderRequestResultUpdateDto();
            updateDto.setOrderNumber(orderRequestMq.getOrderNumber());
            updateDto.setBeforeStatus(OrderRequestResultStatus.RESERVED);
            updateDto.setStatus(OrderRequestResultStatus.EXPIRED);
            updateDto.setFailCode(OrderRequestResultStatus.EXPIRED);
            updateDto.setFailMessage("锁座请求恢复超时");
            return orderRequestResultService.updateStatus(updateDto);
        } catch (RuntimeException e) {
            log.warn("expire reserved order request result failed, orderNumber : {}",
                    orderRequestMq.getOrderNumber(), e);
            return false;
        }
    }

    /**
     * 从锁座快照中解析已锁定座位。
     */
    private List<PurchaseSeat> getReservationPurchaseSeatList(String reservationJson) {
        if (StringUtil.isEmpty(reservationJson)) {
            return null;
        }
        JSONObject reservation = JSON.parseObject(reservationJson);
        JSONArray purchaseSeatArray = reservation.getJSONArray("purchaseSeatList");
        return Objects.isNull(purchaseSeatArray) ? null : purchaseSeatArray.toJavaList(PurchaseSeat.class);
    }

    private boolean isTerminalOrderRequestStatus(String resultStatus) {
        return Objects.equals(resultStatus, OrderRequestResultStatus.ORDER_CREATED) ||
                Objects.equals(resultStatus, OrderRequestResultStatus.FAILED) ||
                Objects.equals(resultStatus, OrderRequestResultStatus.CANCELLED) ||
                Objects.equals(resultStatus, OrderRequestResultStatus.EXPIRED);
    }

    private boolean orderExists(Long orderNumber) {
        return Objects.nonNull(getOrderByNumber(orderNumber));
    }

    private boolean noPayOrderExists(Long orderNumber) {
        OrderGetVo orderGetVo = getOrderByNumber(orderNumber);
        return Objects.nonNull(orderGetVo) && Objects.equals(orderGetVo.getOrderStatus(), OrderStatus.NO_PAY.getCode());
    }

    private OrderGetVo getOrderByNumber(Long orderNumber) {
        OrderGetDto orderGetDto = new OrderGetDto();
        orderGetDto.setOrderNumber(orderNumber);
        try {
            ApiResponse<OrderGetVo> response = orderClient.getStatus(orderGetDto);
            if (response != null && Objects.equals(response.getCode(), BaseCode.SUCCESS.getCode()) &&
                    response.getData() != null) {
                return response.getData();
            }
            if (response != null && Objects.equals(response.getCode(), BaseCode.ORDER_NOT_EXIST.getCode())) {
                return null;
            }
            throw new TikectsystemFrameException(BaseCode.SYSTEM_ERROR);
        } catch (RuntimeException e) {
            log.warn("query order exists failed, orderNumber : {}", orderNumber, e);
            throw e;
        }
    }

    private boolean isRedisInfrastructureException(RuntimeException exception) {
        if (!(exception instanceof TikectsystemFrameException frameException)) {
            return true;
        }
        Integer code = frameException.getCode();
        return code == null || Objects.equals(code, BaseCode.SYSTEM_ERROR.getCode()) ||
                Objects.equals(code, BaseCode.PROGRAM_ORDER_CIRCUIT_OPEN.getCode()) ||
                Objects.equals(code, BaseCode.PROGRAM_ORDER_DEGRADE.getCode());
    }

    private ProgramOrderGateResult gateOrderRequest(ProgramOrderCreateDto programOrderCreateDto, String requestId,
                                                    Long orderNumber) {
        List<String> keys = new ArrayList<>();
        keys.add(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_ORDER_GATE_REQUEST, requestId).getRelKey());
        Long ticketCategoryId = CollectionUtil.isEmpty(programOrderCreateDto.getSeatDtoList()) ?
                programOrderCreateDto.getTicketCategoryId() : programOrderCreateDto.getSeatDtoList().get(0).getTicketCategoryId();
        keys.add(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_ORDER_GATE_INFLIGHT,
                programOrderCreateDto.getProgramId(), ticketCategoryId).getRelKey());
        if (CollectionUtil.isNotEmpty(programOrderCreateDto.getSeatDtoList())) {
            for (SeatDto seatDto : programOrderCreateDto.getSeatDtoList()) {
                keys.add(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_ORDER_GATE_SEAT,
                        programOrderCreateDto.getProgramId() + GLIDE_LINE + seatDto.getId()).getRelKey());
            }
        }
        String type = CollectionUtil.isNotEmpty(programOrderCreateDto.getSeatDtoList()) ? "1" : "2";
        String[] args = new String[]{type, requestId, String.valueOf(orderNumber), "15", "200"};
        return programOrderGateOperate.operate(keys, args);
    }

    private void releaseGate(ProgramOrderCreateDto programOrderCreateDto, String requestId) {
        RedisKeyBuild requestGateKey = RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_ORDER_GATE_REQUEST, requestId);
        String gateOrderNumber = redisCache.get(requestGateKey, String.class);
        boolean requestGateExists = !StringUtil.isEmpty(gateOrderNumber);
        if (requestGateExists) {
            redisCache.del(requestGateKey);
        }
        if (CollectionUtil.isNotEmpty(programOrderCreateDto.getSeatDtoList())) {
            for (SeatDto seatDto : programOrderCreateDto.getSeatDtoList()) {
                RedisKeyBuild seatGateKey = RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_ORDER_GATE_SEAT,
                        programOrderCreateDto.getProgramId() + GLIDE_LINE + seatDto.getId());
                String seatGateRequestId = redisCache.get(seatGateKey, String.class);
                // 只释放当前 requestId 持有的座位 gate，防止晚到回调误删后续请求的 gate。
                if (Objects.equals(seatGateRequestId, requestId)) {
                    redisCache.del(seatGateKey);
                }
            }
            return;
        }
        if (!requestGateExists) {
            return;
        }
        RedisKeyBuild inflightKey = RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_ORDER_GATE_INFLIGHT,
                programOrderCreateDto.getProgramId(), programOrderCreateDto.getTicketCategoryId());
        if (Boolean.TRUE.equals(redisCache.hasKey(inflightKey))) {
            redisCache.incrBy(inflightKey, -1L);
        }
    }

    private void releaseGateSafely(ProgramOrderCreateDto programOrderCreateDto, String requestId) {
        try {
            releaseGate(programOrderCreateDto, requestId);
        } catch (RuntimeException e) {
            log.warn("release order gate failed, requestId : {}", requestId, e);
        }
    }

    private boolean awaitKafkaAck(CountDownLatch latch) {
        try {
            return latch.await(KAFKA_ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TikectsystemFrameException(e);
        }
    }

    private void sendOrderCreateByMq(OrderCreateMq orderCreateMq) {
        CountDownLatch latch = new CountDownLatch(1);
        CreateOrderMqDomain createOrderMqDomain = new CreateOrderMqDomain();
        createOrderMqDomain.orderNumber = String.valueOf(orderCreateMq.getOrderNumber());
        orderKafkaSend.sendOrderCreate(String.valueOf(orderCreateMq.getOrderNumber()), JSON.toJSONString(orderCreateMq),
                sendResult -> latch.countDown(), ex -> {
                    createOrderMqDomain.tikectsystemFrameException = new TikectsystemFrameException(ex);
                    latch.countDown();
                });
        if (!awaitKafkaAck(latch)) {
            throw new TikectsystemFrameException(BaseCode.SYSTEM_ERROR);
        }
        if (Objects.nonNull(createOrderMqDomain.tikectsystemFrameException)) {
            throw createOrderMqDomain.tikectsystemFrameException;
        }
    }

    private Date getReservationExpireTime() {
        return new Date(System.currentTimeMillis() + DELAY_ORDER_CANCEL_TIME_UNIT.toMillis(DELAY_ORDER_CANCEL_TIME));
    }

    private String markOrderRequestReserved(OrderRequestMq orderRequestMq, CreateOrderTemporaryData createOrderTemporaryData) {
        Long orderNumber = orderRequestMq.getOrderNumber();
        if (Objects.isNull(orderNumber)) {
            return null;
        }
        Date expireTime = getReservationExpireTime();
        String reservationJson = buildReservationSnapshot(orderRequestMq, createOrderTemporaryData, expireTime);
        try {
            String redisReservationJson = redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_ORDER_RESERVATION,
                    orderNumber), String.class);
            if (!StringUtil.isEmpty(redisReservationJson)) {
                expireTime = parseReservationExpireTime(redisReservationJson, expireTime);
                if (StringUtil.isEmpty(reservationJson)) {
                    reservationJson = redisReservationJson;
                }
            }
        } catch (RuntimeException e) {
            log.warn("query order reservation snapshot failed, orderNumber : {}", orderNumber, e);
        }
        try {
            orderRequestResultService.markReserved(orderNumber, reservationJson, expireTime);
            OrderRequestResult orderRequestResult = orderRequestResultService.getByOrderNumber(orderNumber);
            return Objects.isNull(orderRequestResult) ? null : orderRequestResult.getResultStatus();
        } catch (RuntimeException e) {
            try {
                OrderRequestResult latestOrderRequestResult = orderRequestResultService.getByOrderNumber(orderNumber);
                if (Objects.nonNull(latestOrderRequestResult) &&
                        !Objects.equals(latestOrderRequestResult.getResultStatus(), OrderRequestResultStatus.PROCESSING)) {
                    return latestOrderRequestResult.getResultStatus();
                }
            } catch (RuntimeException queryException) {
                log.warn("query latest order request status failed, orderNumber : {}", orderNumber, queryException);
            }
            log.warn("mark order request reserved failed, orderNumber : {}", orderNumber, e);
            throw e;
        }
    }

    private String buildReservationSnapshot(OrderRequestMq orderRequestMq, CreateOrderTemporaryData createOrderTemporaryData,
                                            Date expireTime) {
        if (Objects.isNull(createOrderTemporaryData) ||
                CollectionUtil.isEmpty(createOrderTemporaryData.getPurchaseSeatList())) {
            return null;
        }
        ProgramOrderCreateDto programOrderCreateDto = orderRequestMq.getProgramOrderCreateDto();
        JSONObject reservation = new JSONObject();
        reservation.put("orderNumber", String.valueOf(orderRequestMq.getOrderNumber()));
        reservation.put("programId", String.valueOf(programOrderCreateDto.getProgramId()));
        reservation.put("userId", String.valueOf(programOrderCreateDto.getUserId()));
        reservation.put("identifierId", String.valueOf(createOrderTemporaryData.getIdentifierId()));
        reservation.put("expireTime", expireTime.getTime());
        reservation.put("purchaseSeatList", createOrderTemporaryData.getPurchaseSeatList());
        return JSON.toJSONString(reservation);
    }

    private Date parseReservationExpireTime(String reservationJson, Date defaultExpireTime) {
        if (StringUtil.isEmpty(reservationJson)) {
            return defaultExpireTime;
        }
        try {
            Long expireTime = JSON.parseObject(reservationJson).getLong("expireTime");
            return Objects.isNull(expireTime) ? defaultExpireTime : new Date(expireTime);
        } catch (RuntimeException e) {
            log.warn("parse order reservation expire time failed, reservationJson : {}", reservationJson, e);
            return defaultExpireTime;
        }
    }

    private void markOrderRequestFailedSafely(Long orderNumber, Throwable throwable) {
        if (Objects.isNull(orderNumber)) {
            return;
        }
        String failCode = String.valueOf(BaseCode.SYSTEM_ERROR.getCode());
        String failMessage = BaseCode.SYSTEM_ERROR.getMsg();
        if (throwable instanceof TikectsystemFrameException tikectsystemFrameException &&
                Objects.nonNull(tikectsystemFrameException.getCode())) {
            failCode = String.valueOf(tikectsystemFrameException.getCode());
            failMessage = tikectsystemFrameException.getMessage();
        } else if (Objects.nonNull(throwable) && !StringUtil.isEmpty(throwable.getMessage())) {
            failMessage = throwable.getMessage();
        }
        try {
            orderRequestResultService.markFailed(orderNumber, failCode, failMessage);
        } catch (RuntimeException e) {
            log.warn("mark order request failed status error, orderNumber : {}", orderNumber, e);
        }
    }

    private void markOrderRequestCreatedSafely(Long orderNumber) {
        if (Objects.isNull(orderNumber)) {
            return;
        }
        try {
            OrderRequestResultUpdateDto updateDto = new OrderRequestResultUpdateDto();
            updateDto.setOrderNumber(orderNumber);
            updateDto.setBeforeStatus(OrderRequestResultStatus.RESERVED);
            updateDto.setStatus(OrderRequestResultStatus.ORDER_CREATED);
            if (orderRequestResultService.updateStatus(updateDto)) {
                return;
            }
            updateDto.setBeforeStatus(OrderRequestResultStatus.PROCESSING);
            if (orderRequestResultService.updateStatus(updateDto)) {
                return;
            }
            updateDto.setBeforeStatus(OrderRequestResultStatus.FAILED);
            if (orderRequestResultService.updateStatus(updateDto)) {
                return;
            }
            updateDto.setBeforeStatus(OrderRequestResultStatus.EXPIRED);
            if (orderRequestResultService.updateStatus(updateDto)) {
                return;
            }
            updateDto.setBeforeStatus(OrderRequestResultStatus.CANCELLED);
            orderRequestResultService.updateStatus(updateDto);
        } catch (RuntimeException e) {
            log.warn("mark order request created failed, orderNumber : {}", orderNumber, e);
        }
    }

    private void releaseReservation(Long orderNumber, Long programId, List<PurchaseSeat> fallbackPurchaseSeatList) {
        List<PurchaseSeat> purchaseSeatList = fallbackPurchaseSeatList;
        Long releaseProgramId = programId;
        String reservationJson = null;
        OrderRequestResult orderRequestResult = null;
        boolean terminalOrderRequest = false;
        if (Objects.nonNull(orderNumber)) {
            orderRequestResult = orderRequestResultService.getByOrderNumber(orderNumber);
            terminalOrderRequest = Objects.nonNull(orderRequestResult) &&
                    isTerminalOrderRequestStatus(orderRequestResult.getResultStatus());
            if (!terminalOrderRequest) {
                reservationJson = getReleaseReservationJson(orderNumber, orderRequestResult);
            }
        }
        if (!StringUtil.isEmpty(reservationJson)) {
            try {
                JSONObject reservation = JSON.parseObject(reservationJson);
                releaseProgramId = reservation.getLong("programId");
                JSONArray purchaseSeatArray = reservation.getJSONArray("purchaseSeatList");
                if (Objects.nonNull(purchaseSeatArray)) {
                    purchaseSeatList = purchaseSeatArray.toJavaList(PurchaseSeat.class);
                }
            } catch (Exception e) {
                log.warn("parse order reservation snapshot failed, orderNumber : {}", orderNumber, e);
            }
        }
        if (StringUtil.isEmpty(reservationJson) && terminalOrderRequest &&
                CollectionUtil.isEmpty(purchaseSeatList)) {
            return;
        }
        if (releaseProgramId == null || CollectionUtil.isEmpty(purchaseSeatList)) {
            return;
        }
        List<SeatVo> releaseSeatList = new ArrayList<>(purchaseSeatList.size());
        for (PurchaseSeat purchaseSeat : purchaseSeatList) {
            releaseSeatList.add(buildSeatVo(purchaseSeat));
        }
        updateProgramCacheDataResolution(releaseProgramId, releaseSeatList, OrderStatus.CANCEL);
        if (Objects.nonNull(orderNumber)) {
            try {
                redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_ORDER_RESERVATION, orderNumber));
            } catch (RuntimeException e) {
                log.warn("delete order reservation snapshot failed, orderNumber : {}", orderNumber, e);
            }
            clearReservationSafely(orderNumber);
        }
    }

    private void clearReservationSafely(Long orderNumber) {
        try {
            orderRequestResultService.clearReservation(orderNumber);
        } catch (RuntimeException e) {
            log.warn("clear order reservation snapshot failed, orderNumber : {}", orderNumber, e);
        }
    }

    private String getReleaseReservationJson(Long orderNumber, OrderRequestResult orderRequestResult) {
        if (Objects.nonNull(orderRequestResult) && !StringUtil.isEmpty(orderRequestResult.getReservationJson())) {
            return orderRequestResult.getReservationJson();
        }
        String reservationJson = redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_ORDER_RESERVATION,
                orderNumber), String.class);
        if (!StringUtil.isEmpty(reservationJson)) {
            return reservationJson;
        }
        return null;
    }

    private List<PurchaseSeat> buildReleaseFallbackPurchaseSeatList(ProgramOrderCreateDto programOrderCreateDto) {
        if (Objects.isNull(programOrderCreateDto) || CollectionUtil.isEmpty(programOrderCreateDto.getSeatDtoList())) {
            return null;
        }
        List<Long> ticketUserIdList = programOrderCreateDto.getTicketUserIdList();
        List<PurchaseSeat> purchaseSeatList = new ArrayList<>(programOrderCreateDto.getSeatDtoList().size());
        for (int i = 0; i < programOrderCreateDto.getSeatDtoList().size(); i++) {
            SeatDto seatDto = programOrderCreateDto.getSeatDtoList().get(i);
            if (Objects.isNull(seatDto) || Objects.isNull(seatDto.getId()) ||
                    Objects.isNull(seatDto.getTicketCategoryId())) {
                continue;
            }
            PurchaseSeat purchaseSeat = new PurchaseSeat();
            purchaseSeat.setId(seatDto.getId());
            purchaseSeat.setProgramId(programOrderCreateDto.getProgramId());
            purchaseSeat.setTicketCategoryId(seatDto.getTicketCategoryId());
            purchaseSeat.setTicketUserId(CollectionUtil.isEmpty(ticketUserIdList) || i >= ticketUserIdList.size() ?
                    null : ticketUserIdList.get(i));
            purchaseSeat.setRowCode(seatDto.getRowCode());
            purchaseSeat.setColCode(seatDto.getColCode());
            purchaseSeat.setPrice(seatDto.getPrice());
            purchaseSeat.setSellStatus(SellStatus.LOCK.getCode());
            purchaseSeatList.add(purchaseSeat);
        }
        return purchaseSeatList;
    }

    public List<TicketCategoryVo> getTicketCategoryList(ProgramOrderCreateDto programOrderCreateDto, Date showTime) {
        List<TicketCategoryVo> getTicketCategoryVoList = new ArrayList<>();
        List<TicketCategoryVo> ticketCategoryVoList =
                ticketCategoryService.selectTicketCategoryListByProgramIdMultipleCache(programOrderCreateDto.getProgramId(),
                        showTime);
        Map<Long, TicketCategoryVo> ticketCategoryVoMap = new HashMap<>(ticketCategoryVoList.size());
        for (TicketCategoryVo ticketCategoryVo : ticketCategoryVoList) {
            ticketCategoryVoMap.put(ticketCategoryVo.getId(), ticketCategoryVo);
        }
        List<SeatDto> seatDtoList = programOrderCreateDto.getSeatDtoList();
        if (CollectionUtil.isNotEmpty(seatDtoList)) {
            Set<Long> ticketCategoryIdSet = new HashSet<>(seatDtoList.size());
            for (SeatDto seatDto : seatDtoList) {
                if (!ticketCategoryIdSet.add(seatDto.getTicketCategoryId())) {
                    continue;
                }
                TicketCategoryVo ticketCategoryVo = ticketCategoryVoMap.get(seatDto.getTicketCategoryId());
                if (Objects.nonNull(ticketCategoryVo)) {
                    getTicketCategoryVoList.add(ticketCategoryVo);
                } else {
                    throw new TikectsystemFrameException(BaseCode.TICKET_CATEGORY_NOT_EXIST_V2);
                }
            }
        } else {
            TicketCategoryVo ticketCategoryVo = ticketCategoryVoMap.get(programOrderCreateDto.getTicketCategoryId());
            if (Objects.nonNull(ticketCategoryVo)) {
                getTicketCategoryVoList.add(ticketCategoryVo);
            } else {
                throw new TikectsystemFrameException(BaseCode.TICKET_CATEGORY_NOT_EXIST_V2);
            }
        }
        return getTicketCategoryVoList;
    }

    public String create(ProgramOrderCreateDto programOrderCreateDto, Integer orderVersion) {
        ProgramShowTime programShowTime =
                programShowTimeService.selectProgramShowTimeByProgramIdMultipleCache(programOrderCreateDto.getProgramId());
        List<TicketCategoryVo> getTicketCategoryList =
                getTicketCategoryList(programOrderCreateDto, programShowTime.getShowTime());
        Long cacheExpireSeconds = DateUtils.countBetweenSecond(DateUtils.now(), programShowTime.getShowTime());
        BigDecimal parameterOrderPrice = new BigDecimal("0");
        BigDecimal databaseOrderPrice = new BigDecimal("0");
        List<SeatVo> purchaseSeatList = new ArrayList<>();
        List<SeatDto> seatDtoList = programOrderCreateDto.getSeatDtoList();
        List<SeatVo> seatVoList = new ArrayList<>();
        Map<String, Long> ticketCategoryRemainNumber = new HashMap<>(16);
        for (TicketCategoryVo ticketCategory : getTicketCategoryList) {
            List<SeatVo> allSeatVoList =
                    seatService.selectSeatResolution(programOrderCreateDto.getProgramId(), ticketCategory.getId(),
                            cacheExpireSeconds, TimeUnit.SECONDS);
            seatVoList.addAll(allSeatVoList.stream().
                    filter(seatVo -> Objects.equals(seatVo.getSellStatus(),SellStatus.NO_SOLD.getCode())).toList());
            ticketCategoryRemainNumber.putAll(ticketCategoryService.getRedisRemainNumberResolution(
                    programOrderCreateDto.getProgramId(), ticketCategory.getId()));
        }
        if (CollectionUtil.isNotEmpty(seatDtoList)) {
            Map<Long, Long> seatTicketCategoryDtoCount = seatDtoList.stream()
                    .collect(Collectors.groupingBy(SeatDto::getTicketCategoryId, Collectors.counting()));
            for (Entry<Long, Long> entry : seatTicketCategoryDtoCount.entrySet()) {
                Long ticketCategoryId = entry.getKey();
                Long purchaseCount = entry.getValue();
                Long remainNumber = Optional.ofNullable(ticketCategoryRemainNumber.get(String.valueOf(ticketCategoryId)))
                        .orElseThrow(() -> new TikectsystemFrameException(BaseCode.TICKET_CATEGORY_NOT_EXIST_V2));
                if (purchaseCount > remainNumber) {
                    throw new TikectsystemFrameException(BaseCode.TICKET_REMAIN_NUMBER_NOT_SUFFICIENT);
                }
            }
            Map<String, SeatVo> seatVoMap = seatVoList.stream().collect(Collectors
                    .toMap(seat -> seat.getRowCode() + "-" + seat.getColCode(), seat -> seat, (v1, v2) -> v2));
            for (SeatDto seatDto : seatDtoList) {
                SeatVo seatVo = seatVoMap.get(seatDto.getRowCode() + "-" + seatDto.getColCode());
                if (Objects.isNull(seatVo)) {
                    throw new TikectsystemFrameException(BaseCode.SEAT_IS_NOT_NOT_SOLD);
                }
                purchaseSeatList.add(seatVo);
                parameterOrderPrice = parameterOrderPrice.add(seatDto.getPrice());
                databaseOrderPrice = databaseOrderPrice.add(seatVo.getPrice());
            }
            if (parameterOrderPrice.compareTo(databaseOrderPrice) > 0) {
                throw new TikectsystemFrameException(BaseCode.PRICE_ERROR);
            }
        } else {
            Long ticketCategoryId = programOrderCreateDto.getTicketCategoryId();
            Integer ticketCount = programOrderCreateDto.getTicketCount();
            Long remainNumber = Optional.ofNullable(ticketCategoryRemainNumber.get(String.valueOf(ticketCategoryId)))
                    .orElseThrow(() -> new TikectsystemFrameException(BaseCode.TICKET_CATEGORY_NOT_EXIST_V2));
            if (ticketCount > remainNumber) {
                throw new TikectsystemFrameException(BaseCode.TICKET_REMAIN_NUMBER_NOT_SUFFICIENT);
            }
            purchaseSeatList = SeatMatch.findAdjacentSeatVos(seatVoList.stream().filter(seatVo ->
                    Objects.equals(seatVo.getTicketCategoryId(), ticketCategoryId)).collect(Collectors.toList()), ticketCount);
            if (purchaseSeatList.size() < ticketCount) {
                throw new TikectsystemFrameException(BaseCode.SEAT_OCCUPY);
            }
        }
        Integer movedSeatCount =
                updateProgramCacheDataResolution(programOrderCreateDto.getProgramId(), purchaseSeatList, OrderStatus.NO_PAY);
        if (!Objects.equals(movedSeatCount, purchaseSeatList.size())) {
            throw new TikectsystemFrameException(BaseCode.SEAT_OCCUPY);
        }
        return doCreate(programOrderCreateDto, purchaseSeatList, orderVersion);
    }


    public String createNew(ProgramOrderCreateDto programOrderCreateDto, Integer orderVersion) {
        CreateOrderTemporaryData createOrderTemporaryData = createOrderOperateProgramCacheResolution(programOrderCreateDto);
        return createByTemporaryData(programOrderCreateDto, createOrderTemporaryData, orderVersion);
    }

    public String createByTemporaryData(ProgramOrderCreateDto programOrderCreateDto,
                                        CreateOrderTemporaryData createOrderTemporaryData,
                                        Integer orderVersion) {
        if (createOrderTemporaryData == null || CollectionUtil.isEmpty(createOrderTemporaryData.getPurchaseSeatList())) {
            throw new TikectsystemFrameException(BaseCode.SEAT_NOT_EXIST);
        }
        List<PurchaseSeat> purchaseSeatTemporaryList = createOrderTemporaryData.getPurchaseSeatList();
        List<SeatVo> purchaseSeatList = new ArrayList<>(purchaseSeatTemporaryList.size());
        for (PurchaseSeat purchaseSeat : purchaseSeatTemporaryList) {
            purchaseSeatList.add(buildSeatVo(purchaseSeat));
        }
        return doCreate(programOrderCreateDto, purchaseSeatList, orderVersion);
    }

    public String createNewAsync(ProgramOrderCreateDto programOrderCreateDto, Integer orderVersion) {
        //操作redis
        CreateOrderTemporaryData createOrderTemporaryData = createOrderOperateProgramCacheResolution(programOrderCreateDto);
        //发送kafka
        return createByTemporaryDataAsync(programOrderCreateDto, createOrderTemporaryData, orderVersion);
    }

    public String createByTemporaryDataAsync(ProgramOrderCreateDto programOrderCreateDto,
                                             CreateOrderTemporaryData createOrderTemporaryData,
                                             Integer orderVersion) {
        return doCreateV2(programOrderCreateDto, createOrderTemporaryData, orderVersion);
    }

    public CreateOrderTemporaryData createOrderOperateProgramCacheResolution(ProgramOrderCreateDto programOrderCreateDto) {
        prepareCreateOrderProgramCache(programOrderCreateDto);
        return createOrderOperateProgramCache(programOrderCreateDto);
    }

    public void prepareCreateOrderProgramCache(ProgramOrderCreateDto programOrderCreateDto) {
        //从多级缓存中查找节目演出时间ProgramShowTime
        ProgramShowTime programShowTime =
                programShowTimeService.selectProgramShowTimeByProgramIdMultipleCache(programOrderCreateDto.getProgramId());
        //查询对应的票档类型
        List<TicketCategoryVo> getTicketCategoryList =
                getTicketCategoryList(programOrderCreateDto, programShowTime.getShowTime());
        Long cacheExpireSeconds = DateUtils.countBetweenSecond(DateUtils.now(), programShowTime.getShowTime());
        //遍历得到的票档
        for (TicketCategoryVo ticketCategory : getTicketCategoryList) {
            //从缓存中查询座位，如果缓存不存在，则从数据库查询后再放入缓存
            seatService.selectSeatResolution(programOrderCreateDto.getProgramId(), ticketCategory.getId(),
                    cacheExpireSeconds, TimeUnit.SECONDS);
            //从缓存中查询余票数量，如果缓存不存在，则从数据库查询后再放入缓存
            ticketCategoryService.getRedisRemainNumberResolution(
                    programOrderCreateDto.getProgramId(), ticketCategory.getId());
        }
    }

    public CreateOrderTemporaryData createOrderOperateProgramCache(ProgramOrderCreateDto programOrderCreateDto) {
        return createOrderOperateProgramCache(programOrderCreateDto, null);
    }

    public CreateOrderTemporaryData createOrderOperateProgramCache(ProgramOrderCreateDto programOrderCreateDto, Long orderNumber) {
        Long programId = programOrderCreateDto.getProgramId();
        List<SeatDto> seatDtoList = programOrderCreateDto.getSeatDtoList();
        List<String> keys = new ArrayList<>();
        String[] data = new String[5];
        data[3] = "";
        data[4] = "0";
        //更新票档数据集合
        JSONArray jsonArray = new JSONArray();
        //添加座位数据集合
        JSONArray addSeatDatajsonArray = new JSONArray();
        if (CollectionUtil.isNotEmpty(seatDtoList)) {
            keys.add("1");
            Map<Long, List<SeatDto>> seatTicketCategoryDtoCount = new HashMap<>(seatDtoList.size());
            for (SeatDto seatDto : seatDtoList) {
                seatTicketCategoryDtoCount.computeIfAbsent(seatDto.getTicketCategoryId(), key -> new ArrayList<>()).add(seatDto);
            }
            for (Entry<Long, List<SeatDto>> entry : seatTicketCategoryDtoCount.entrySet()) {
                Long ticketCategoryId = entry.getKey();
                int ticketCount = entry.getValue().size();
                //这里是计算更新票档数据
                JSONObject jsonObject = new JSONObject();
                //票档数量的key
                jsonObject.put("programTicketRemainNumberHashKey", RedisKeyBuild.createRedisKey(
                        RedisKeyManage.PROGRAM_TICKET_REMAIN_NUMBER_HASH_RESOLUTION, programId, ticketCategoryId).getRelKey());
                //票档id
                jsonObject.put("ticketCategoryId", String.valueOf(ticketCategoryId));
                //扣减余票数量
                jsonObject.put("ticketCount", ticketCount);
                jsonArray.add(jsonObject);

                JSONObject seatDatajsonObject = new JSONObject();
                //未售卖座位的hash的key
                seatDatajsonObject.put("seatNoSoldHashKey", RedisKeyBuild.createRedisKey(
                        RedisKeyManage.PROGRAM_SEAT_NO_SOLD_RESOLUTION_HASH, programId, ticketCategoryId).getRelKey());
                //座位数据
                seatDatajsonObject.put("ticketCategoryId", String.valueOf(ticketCategoryId));
                seatDatajsonObject.put("seatDataList", JSON.toJSONString(buildLuaSeatDtoList(entry.getValue())));
                addSeatDatajsonArray.add(seatDatajsonObject);
            }
        } else {
            keys.add("2");
            Long ticketCategoryId = programOrderCreateDto.getTicketCategoryId();
            Integer ticketCount = programOrderCreateDto.getTicketCount();
            JSONObject jsonObject = new JSONObject();
            //票档数量的key
            jsonObject.put("programTicketRemainNumberHashKey", RedisKeyBuild.createRedisKey(
                    RedisKeyManage.PROGRAM_TICKET_REMAIN_NUMBER_HASH_RESOLUTION, programId, ticketCategoryId).getRelKey());
            //票档id
            jsonObject.put("ticketCategoryId", String.valueOf(ticketCategoryId));
            //扣减余票数量
            jsonObject.put("ticketCount", ticketCount);
            //未售卖座位的hash的key
            jsonObject.put("seatNoSoldHashKey", RedisKeyBuild.createRedisKey(
                    RedisKeyManage.PROGRAM_SEAT_NO_SOLD_RESOLUTION_HASH, programId, ticketCategoryId).getRelKey());
            jsonArray.add(jsonObject);
        }
        //未售卖座位hash的key(占位符形式)
        keys.add(RedisKeyBuild.getRedisKey(RedisKeyManage.PROGRAM_SEAT_NO_SOLD_RESOLUTION_HASH));
        //锁定座位hash的key(占位符形式)
        keys.add(RedisKeyBuild.getRedisKey(RedisKeyManage.PROGRAM_SEAT_LOCK_RESOLUTION_HASH));
        keys.add(String.valueOf(programOrderCreateDto.getProgramId()));
        //记录的key(占位符形式)
        keys.add(RedisKeyBuild.getRedisKey(RedisKeyManage.PROGRAM_RECORD));
        //记录的标识
        Long identifierId = uidGenerator.getUid();
        //把记录的标识id放进去
        keys.add(RecordType.REDUCE.getValue() + GLIDE_LINE + identifierId + GLIDE_LINE + programOrderCreateDto.getUserId());
        //记录的类型
        keys.add(RecordType.REDUCE.getValue());
        if (Objects.nonNull(orderNumber)) {
            keys.add(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_ORDER_RESERVATION, orderNumber).getRelKey());
        }
        data[0] = JSON.toJSONString(jsonArray);
        data[1] = JSON.toJSONString(addSeatDatajsonArray);
        //购票人id集合
        data[2] = JSON.toJSONString(programOrderCreateDto.getTicketUserIdList()
                .stream().map(String::valueOf).collect(Collectors.toList()));
        if (Objects.nonNull(orderNumber)) {
            JSONObject reservation = new JSONObject();
            reservation.put("orderNumber", String.valueOf(orderNumber));
            reservation.put("programId", String.valueOf(programId));
            reservation.put("userId", String.valueOf(programOrderCreateDto.getUserId()));
            reservation.put("identifierId", String.valueOf(identifierId));
            reservation.put("expireTime", getReservationExpireTime().getTime());
            data[3] = JSON.toJSONString(reservation);
            data[4] = String.valueOf(DELAY_ORDER_CANCEL_TIME_UNIT.toSeconds(DELAY_ORDER_CANCEL_TIME));
        }
        //执行lua脚本
        ProgramCacheCreateOrderData programCacheCreateOrderData =
                programCacheCreateOrderResolutionOperate.programCacheOperate(keys, data);
        if (programCacheCreateOrderData == null) {
            throw new TikectsystemFrameException(BaseCode.SYSTEM_ERROR);
        }
        if (!Objects.equals(programCacheCreateOrderData.getCode(), BaseCode.SUCCESS.getCode())) {
            BaseCode baseCode = BaseCode.getRc(programCacheCreateOrderData.getCode());
            throw new TikectsystemFrameException(baseCode == null ? BaseCode.SYSTEM_ERROR : baseCode);
        }
        if (Objects.nonNull(programCacheCreateOrderData.getIdentifierId())) {
            identifierId = programCacheCreateOrderData.getIdentifierId();
        }
        List<PurchaseSeat> purchaseSeatList = programCacheCreateOrderData.getPurchaseSeatList();
        try {
            rebindTicketUserIds(programOrderCreateDto, purchaseSeatList);
            validatePurchaseSeats(programOrderCreateDto, purchaseSeatList);
        } catch (RuntimeException e) {
            releaseReservation(orderNumber, programId, purchaseSeatList);
            throw e;
        }
        return new CreateOrderTemporaryData(identifierId, purchaseSeatList);
    }

    private JSONArray buildLuaSeatDtoList(List<SeatDto> seatDtoList) {
        JSONArray seatArray = new JSONArray();
        for (SeatDto seatDto : seatDtoList) {
            JSONObject seat = new JSONObject();
            seat.put("id", String.valueOf(seatDto.getId()));
            seat.put("ticketCategoryId", String.valueOf(seatDto.getTicketCategoryId()));
            seat.put("rowCode", seatDto.getRowCode());
            seat.put("colCode", seatDto.getColCode());
            seat.put("price", seatDto.getPrice());
            seatArray.add(seat);
        }
        return seatArray;
    }

    private void validatePurchaseSeats(ProgramOrderCreateDto programOrderCreateDto, List<PurchaseSeat> purchaseSeatList) {
        if (CollectionUtil.isEmpty(purchaseSeatList)) {
            throw new TikectsystemFrameException(BaseCode.SEAT_NOT_EXIST);
        }
        List<SeatDto> seatDtoList = programOrderCreateDto.getSeatDtoList();
        if (CollectionUtil.isEmpty(seatDtoList)) {
            validateAutoMatchPurchaseSeats(programOrderCreateDto, purchaseSeatList);
            return;
        }
        if (purchaseSeatList.size() != seatDtoList.size()) {
            throw new TikectsystemFrameException(BaseCode.TICKET_USER_COUNT_UNEQUAL_SEAT_COUNT);
        }
        Map<Long, SeatDto> seatDtoMap = seatDtoList.stream()
                .collect(Collectors.toMap(SeatDto::getId, seatDto -> seatDto, (v1, v2) -> v2));
        for (PurchaseSeat purchaseSeat : purchaseSeatList) {
            if (Objects.isNull(purchaseSeat) || Objects.isNull(purchaseSeat.getId())) {
                throw new TikectsystemFrameException(BaseCode.SEAT_NOT_EXIST);
            }
            SeatDto sourceSeatDto = seatDtoMap.get(purchaseSeat.getId());
            if (Objects.isNull(sourceSeatDto)) {
                log.warn("purchase seat id mismatch after lua, programId:{}, sourceSeatIds:{}, purchaseSeat:{}",
                        programOrderCreateDto.getProgramId(), seatDtoMap.keySet(), JSON.toJSONString(purchaseSeat));
                throw new TikectsystemFrameException(BaseCode.SEAT_NOT_EXIST);
            }
            if (!Objects.equals(sourceSeatDto.getTicketCategoryId(), purchaseSeat.getTicketCategoryId())) {
                log.warn("purchase seat ticket category mismatch after lua, programId:{}, seatId:{}, sourceTicketCategoryId:{}, purchaseTicketCategoryId:{}",
                        programOrderCreateDto.getProgramId(), purchaseSeat.getId(), sourceSeatDto.getTicketCategoryId(),
                        purchaseSeat.getTicketCategoryId());
                throw new TikectsystemFrameException(BaseCode.PRICE_ERROR);
            }
        }
    }

    private void validateAutoMatchPurchaseSeats(ProgramOrderCreateDto programOrderCreateDto,
                                                List<PurchaseSeat> purchaseSeatList) {
        Long ticketCategoryId = programOrderCreateDto.getTicketCategoryId();
        Integer ticketCount = programOrderCreateDto.getTicketCount();
        if (purchaseSeatList.size() != ticketCount) {
            throw new TikectsystemFrameException(BaseCode.TICKET_USER_COUNT_UNEQUAL_SEAT_COUNT);
        }
        ProgramShowTime programShowTime =
                programShowTimeService.selectProgramShowTimeByProgramIdMultipleCache(programOrderCreateDto.getProgramId());
        TicketCategoryVo ticketCategoryVo = getTicketCategoryList(programOrderCreateDto, programShowTime.getShowTime())
                .stream()
                .filter(ticketCategory -> Objects.equals(ticketCategory.getId(), ticketCategoryId))
                .findFirst()
                .orElseThrow(() -> new TikectsystemFrameException(BaseCode.TICKET_CATEGORY_NOT_EXIST_V2));
        if (Objects.isNull(ticketCategoryVo.getPrice())) {
            throw new TikectsystemFrameException(BaseCode.PRICE_ERROR);
        }
        for (PurchaseSeat purchaseSeat : purchaseSeatList) {
            if (Objects.isNull(purchaseSeat) || Objects.isNull(purchaseSeat.getTicketCategoryId()) ||
                    Objects.isNull(purchaseSeat.getPrice())) {
                throw new TikectsystemFrameException(BaseCode.SEAT_NOT_EXIST);
            }
            if (!Objects.equals(ticketCategoryId, purchaseSeat.getTicketCategoryId())) {
                log.warn("auto match ticket category mismatch after lua, programId:{}, sourceTicketCategoryId:{}, purchaseSeat:{}",
                        programOrderCreateDto.getProgramId(), ticketCategoryId, JSON.toJSONString(purchaseSeat));
                throw new TikectsystemFrameException(BaseCode.PRICE_ERROR);
            }
            if (ticketCategoryVo.getPrice().compareTo(purchaseSeat.getPrice()) != 0) {
                log.warn("auto match price mismatch after lua, programId:{}, ticketCategoryId:{}, categoryPrice:{}, seatPrice:{}, seatId:{}",
                        programOrderCreateDto.getProgramId(), ticketCategoryId, ticketCategoryVo.getPrice(),
                        purchaseSeat.getPrice(), purchaseSeat.getId());
                throw new TikectsystemFrameException(BaseCode.PRICE_ERROR);
            }
        }
    }

    private void rebindTicketUserIds(ProgramOrderCreateDto programOrderCreateDto, List<PurchaseSeat> purchaseSeatList) {
        List<Long> ticketUserIdList = programOrderCreateDto.getTicketUserIdList();
        if (CollectionUtil.isEmpty(ticketUserIdList) || CollectionUtil.isEmpty(purchaseSeatList) ||
                ticketUserIdList.size() != purchaseSeatList.size()) {
            throw new TikectsystemFrameException(BaseCode.TICKET_USER_COUNT_UNEQUAL_SEAT_COUNT);
        }
        for (int i = 0; i < purchaseSeatList.size(); i++) {
            PurchaseSeat purchaseSeat = purchaseSeatList.get(i);
            if (Objects.isNull(purchaseSeat)) {
                throw new TikectsystemFrameException(BaseCode.SEAT_NOT_EXIST);
            }
            Long sourceTicketUserId = ticketUserIdList.get(i);
            if (!Objects.equals(purchaseSeat.getTicketUserId(), sourceTicketUserId)) {
                log.warn("rebinding ticketUserId after lua, programId:{}, seatId:{}, luaTicketUserId:{}, sourceTicketUserId:{}",
                        programOrderCreateDto.getProgramId(), purchaseSeat.getId(), purchaseSeat.getTicketUserId(), sourceTicketUserId);
                purchaseSeat.setTicketUserId(sourceTicketUserId);
            }
        }
    }

    private String doCreate(ProgramOrderCreateDto programOrderCreateDto, List<SeatVo> purchaseSeatList, Integer orderVersion) {
        OrderCreateDto orderCreateDto = buildCreateOrderParam(programOrderCreateDto, purchaseSeatList, orderVersion);

        String orderNumber = createOrderByRpc(orderCreateDto, purchaseSeatList);

        DelayOrderCancelDto delayOrderCancelDto = new DelayOrderCancelDto();
        delayOrderCancelDto.setProgramId(programOrderCreateDto.getProgramId());
        delayOrderCancelDto.setOrderNumber(orderCreateDto.getOrderNumber());
        delayOrderCancelSend.sendMessage(delayOrderCancelDto);

        return orderNumber;
    }

    private String doCreateV2(ProgramOrderCreateDto programOrderCreateDto,
                              CreateOrderTemporaryData createOrderTemporaryData,
                              Integer orderVersion) {
        if (createOrderTemporaryData == null || CollectionUtil.isEmpty(createOrderTemporaryData.getPurchaseSeatList())) {
            throw new TikectsystemFrameException(BaseCode.SEAT_NOT_EXIST);
        }
        OrderCreateDto orderCreateDto = buildCreateOrderParamV2(programOrderCreateDto.getProgramId(),
                programOrderCreateDto.getUserId(), createOrderTemporaryData.getPurchaseSeatList(), orderVersion);
        OrderCreateMq orderCreateMq = buildOrderCreateMq(orderCreateDto, createOrderTemporaryData.getIdentifierId());
        //插入节目记录任务
        programRecordTaskExecutor.execute(() -> createProgramRecordTask(orderCreateMq.getProgramId()));
        //创建订单
        String orderNumber = createOrderByMq(orderCreateMq, createOrderTemporaryData.getPurchaseSeatList());
        DelayOrderCancelDto delayOrderCancelDto = new DelayOrderCancelDto();
        delayOrderCancelDto.setProgramId(orderCreateDto.getProgramId());
        delayOrderCancelDto.setOrderNumber(orderCreateDto.getOrderNumber());
        delayOrderCancelSend.sendMessage(delayOrderCancelDto);

        return orderNumber;
    }

    public void createProgramRecordTask(Long programId) {
        ProgramRecordTask programRecordTask = new ProgramRecordTask();
        programRecordTask.setId(uidGenerator.getUid());
        programRecordTask.setProgramId(programId);
        programRecordTask.setCreateTime(DateUtils.now());
        programRecordTask.setEditTime(DateUtils.now());
        programRecordTaskMapper.insert(programRecordTask);
    }

    private OrderCreateDto buildCreateOrderParam(ProgramOrderCreateDto programOrderCreateDto,
                                                 List<SeatVo> purchaseSeatList,
                                                 Integer orderVersion) {
        ProgramVo programVo = programService.simpleGetProgramAndShowMultipleCache(programOrderCreateDto.getProgramId());
        OrderCreateDto orderCreateDto = new OrderCreateDto();
        orderCreateDto.setOrderNumber(uidGenerator.getOrderNumber(programOrderCreateDto.getUserId(), ORDER_TABLE_COUNT));
        orderCreateDto.setProgramId(programOrderCreateDto.getProgramId());
        orderCreateDto.setProgramItemPicture(programVo.getItemPicture());
        orderCreateDto.setUserId(programOrderCreateDto.getUserId());
        orderCreateDto.setProgramTitle(programVo.getTitle());
        orderCreateDto.setProgramPlace(programVo.getPlace());
        orderCreateDto.setProgramShowTime(programVo.getShowTime());
        orderCreateDto.setProgramPermitChooseSeat(programVo.getPermitChooseSeat());
        BigDecimal databaseOrderPrice = BigDecimal.ZERO;
        for (SeatVo seatVo : purchaseSeatList) {
            databaseOrderPrice = databaseOrderPrice.add(seatVo.getPrice());
        }
        Date createOrderTime = DateUtils.now();
        orderCreateDto.setOrderPrice(databaseOrderPrice);
        orderCreateDto.setCreateOrderTime(createOrderTime);
        orderCreateDto.setOrderVersion(orderVersion);

        List<Long> ticketUserIdList = programOrderCreateDto.getTicketUserIdList();
        if (CollectionUtil.isEmpty(ticketUserIdList) || CollectionUtil.isEmpty(purchaseSeatList) ||
                ticketUserIdList.size() != purchaseSeatList.size()) {
            throw new TikectsystemFrameException(BaseCode.TICKET_USER_COUNT_UNEQUAL_SEAT_COUNT);
        }
        List<OrderTicketUserCreateDto> orderTicketUserCreateDtoList = new ArrayList<>(purchaseSeatList.size());
        for (int i = 0; i < ticketUserIdList.size(); i++) {
            Long ticketUserId = ticketUserIdList.get(i);
            OrderTicketUserCreateDto orderTicketUserCreateDto = new OrderTicketUserCreateDto();
            orderTicketUserCreateDto.setOrderNumber(orderCreateDto.getOrderNumber());
            orderTicketUserCreateDto.setProgramId(programOrderCreateDto.getProgramId());
            orderTicketUserCreateDto.setUserId(programOrderCreateDto.getUserId());
            orderTicketUserCreateDto.setTicketUserId(ticketUserId);
            SeatVo seatVo = purchaseSeatList.get(i);
            if (seatVo == null) {
                throw new TikectsystemFrameException(BaseCode.SEAT_NOT_EXIST);
            }
            orderTicketUserCreateDto.setSeatId(seatVo.getId());
            orderTicketUserCreateDto.setSeatInfo(seatVo.getRowCode() + "排" + seatVo.getColCode() + "列");
            orderTicketUserCreateDto.setTicketCategoryId(seatVo.getTicketCategoryId());
            orderTicketUserCreateDto.setOrderPrice(seatVo.getPrice());
            orderTicketUserCreateDto.setCreateOrderTime(createOrderTime);
            orderTicketUserCreateDtoList.add(orderTicketUserCreateDto);
        }

        orderCreateDto.setOrderTicketUserCreateDtoList(orderTicketUserCreateDtoList);

        return orderCreateDto;
    }

    private OrderCreateDto buildCreateOrderParamV2(Long programId, Long userId, List<PurchaseSeat> purchaseSeatList, Integer orderVersion) {
        return buildCreateOrderParamV2(programId, userId, purchaseSeatList, orderVersion, null);
    }

    private OrderCreateDto buildCreateOrderParamV2(Long programId, Long userId, List<PurchaseSeat> purchaseSeatList,
                                                   Integer orderVersion, Long orderNumber) {
        if (CollectionUtil.isEmpty(purchaseSeatList)) {
            throw new TikectsystemFrameException(BaseCode.SEAT_NOT_EXIST);
        }
        ProgramVo programVo = programService.simpleGetProgramAndShowMultipleCache(programId);
        OrderCreateDto orderCreateDto = new OrderCreateDto();
        orderCreateDto.setOrderNumber(Objects.nonNull(orderNumber) ? orderNumber :
                uidGenerator.getOrderNumber(userId, ORDER_TABLE_COUNT));
        orderCreateDto.setProgramId(programId);
        orderCreateDto.setProgramItemPicture(programVo.getItemPicture());
        orderCreateDto.setUserId(userId);
        orderCreateDto.setProgramTitle(programVo.getTitle());
        orderCreateDto.setProgramPlace(programVo.getPlace());
        orderCreateDto.setProgramShowTime(programVo.getShowTime());
        orderCreateDto.setProgramPermitChooseSeat(programVo.getPermitChooseSeat());
        BigDecimal databaseOrderPrice = BigDecimal.ZERO;
        for (PurchaseSeat purchaseSeat : purchaseSeatList) {
            databaseOrderPrice = databaseOrderPrice.add(purchaseSeat.getPrice());
        }
        Date createOrderTime = DateUtils.now();
        orderCreateDto.setOrderPrice(databaseOrderPrice);
        orderCreateDto.setCreateOrderTime(createOrderTime);
        orderCreateDto.setOrderVersion(orderVersion);

        List<OrderTicketUserCreateDto> orderTicketUserCreateDtoList = new ArrayList<>(purchaseSeatList.size());
        for (PurchaseSeat purchaseSeat : purchaseSeatList) {
            OrderTicketUserCreateDto orderTicketUserCreateDto = new OrderTicketUserCreateDto();
            orderTicketUserCreateDto.setOrderNumber(orderCreateDto.getOrderNumber());
            orderTicketUserCreateDto.setProgramId(programId);
            orderTicketUserCreateDto.setUserId(userId);
            orderTicketUserCreateDto.setTicketUserId(purchaseSeat.getTicketUserId());
            orderTicketUserCreateDto.setSeatId(purchaseSeat.getId());
            orderTicketUserCreateDto.setSeatInfo(purchaseSeat.getRowCode() + "排" + purchaseSeat.getColCode() + "列");
            orderTicketUserCreateDto.setTicketCategoryId(purchaseSeat.getTicketCategoryId());
            orderTicketUserCreateDto.setOrderPrice(purchaseSeat.getPrice());
            orderTicketUserCreateDto.setCreateOrderTime(createOrderTime);
            orderTicketUserCreateDtoList.add(orderTicketUserCreateDto);
        }
        orderCreateDto.setOrderTicketUserCreateDtoList(orderTicketUserCreateDtoList);
        return orderCreateDto;
    }

    private String createOrderByRpc(OrderCreateDto orderCreateDto, List<SeatVo> purchaseSeatList) {
        ApiResponse<String> createOrderResponse = orderClient.create(orderCreateDto);
        if (createOrderResponse == null) {
            log.error("创建订单RPC返回为空 orderCreateDto : {}", JSON.toJSONString(orderCreateDto));
            throw new TikectsystemFrameException(BaseCode.RPC_RESULT_DATA_EMPTY);
        }
        if (!Objects.equals(createOrderResponse.getCode(), BaseCode.SUCCESS.getCode())) {
            log.error("创建订单失败 需人工处理 orderCreateDto : {}", JSON.toJSONString(orderCreateDto));
            updateProgramCacheDataResolution(orderCreateDto.getProgramId(), purchaseSeatList, OrderStatus.CANCEL);
            throw new TikectsystemFrameException(createOrderResponse);
        }
        if (StringUtil.isEmpty(createOrderResponse.getData())) {
            throw new TikectsystemFrameException(BaseCode.RPC_RESULT_DATA_EMPTY);
        }
        return createOrderResponse.getData();
    }

    private String createOrderByMq(OrderCreateMq orderCreateMq, List<PurchaseSeat> purchaseSeatList) {
        CreateOrderMqDomain createOrderMqDomain = new CreateOrderMqDomain();
        CountDownLatch latch = new CountDownLatch(1);
        createOrderMqDomain.orderNumber = String.valueOf(orderCreateMq.getOrderNumber());
        createOrderSend.sendMessage(JSON.toJSONString(orderCreateMq), sendResult -> {
            if (log.isDebugEnabled()) {
                String topic = sendResult == null || sendResult.getRecordMetadata() == null ? null :
                        sendResult.getRecordMetadata().topic();
                log.debug("create order kafka send success, topic : {}", topic);
            }
            latch.countDown();
        }, ex -> {
            log.error("创建订单kafka发送消息失败 error", ex);
            List<SeatVo> purchaseSeatVoList = new ArrayList<>(purchaseSeatList.size());
            for (PurchaseSeat purchaseSeat : purchaseSeatList) {
                purchaseSeatVoList.add(buildSeatVo(purchaseSeat));
            }
            updateProgramCacheDataResolution(orderCreateMq.getProgramId(), purchaseSeatVoList, OrderStatus.CANCEL);
            createOrderMqDomain.tikectsystemFrameException = new TikectsystemFrameException(ex);
            latch.countDown();
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("createOrderByMq InterruptedException", e);
            throw new TikectsystemFrameException(e);
        }
        if (Objects.nonNull(createOrderMqDomain.tikectsystemFrameException)) {
            throw createOrderMqDomain.tikectsystemFrameException;
        }
        return createOrderMqDomain.orderNumber;
    }

    private OrderCreateMq buildOrderCreateMq(OrderCreateDto orderCreateDto, Long identifierId) {
        OrderCreateMq orderCreateMq = new OrderCreateMq();
        orderCreateMq.setIdentifierId(identifierId);
        orderCreateMq.setOrderNumber(orderCreateDto.getOrderNumber());
        orderCreateMq.setProgramId(orderCreateDto.getProgramId());
        orderCreateMq.setProgramItemPicture(orderCreateDto.getProgramItemPicture());
        orderCreateMq.setUserId(orderCreateDto.getUserId());
        orderCreateMq.setProgramTitle(orderCreateDto.getProgramTitle());
        orderCreateMq.setProgramPlace(orderCreateDto.getProgramPlace());
        orderCreateMq.setProgramShowTime(orderCreateDto.getProgramShowTime());
        orderCreateMq.setProgramPermitChooseSeat(orderCreateDto.getProgramPermitChooseSeat());
        orderCreateMq.setDistributionMode(orderCreateDto.getDistributionMode());
        orderCreateMq.setTakeTicketMode(orderCreateDto.getTakeTicketMode());
        orderCreateMq.setOrderPrice(orderCreateDto.getOrderPrice());
        orderCreateMq.setCreateOrderTime(orderCreateDto.getCreateOrderTime());
        orderCreateMq.setOrderTicketUserCreateDtoList(orderCreateDto.getOrderTicketUserCreateDtoList());
        orderCreateMq.setOrderVersion(orderCreateDto.getOrderVersion());
        return orderCreateMq;
    }

    private SeatVo buildSeatVo(PurchaseSeat purchaseSeat) {
        SeatVo seatVo = new SeatVo();
        seatVo.setId(purchaseSeat.getId());
        seatVo.setProgramId(purchaseSeat.getProgramId());
        seatVo.setTicketCategoryId(purchaseSeat.getTicketCategoryId());
        seatVo.setRowCode(purchaseSeat.getRowCode());
        seatVo.setColCode(purchaseSeat.getColCode());
        seatVo.setSeatType(purchaseSeat.getSeatType());
        seatVo.setSeatTypeName(purchaseSeat.getSeatTypeName());
        seatVo.setPrice(purchaseSeat.getPrice());
        seatVo.setSellStatus(purchaseSeat.getSellStatus());
        return seatVo;
    }

    private Integer updateProgramCacheDataResolution(Long programId, List<SeatVo> seatVoList, OrderStatus orderStatus) {
        //如果要操作的订单状态不是未支付和取消，那么直接拒绝
        if (!(Objects.equals(orderStatus.getCode(), OrderStatus.NO_PAY.getCode()) ||
                Objects.equals(orderStatus.getCode(), OrderStatus.CANCEL.getCode()))) {
            throw new TikectsystemFrameException(BaseCode.OPERATE_ORDER_STATUS_NOT_PERMIT);
        }
        List<String> keys = new ArrayList<>();
        //这里key只是占位，并不起实际作用
        keys.add("#");

        String[] data = new String[3];
        Map<Long, Long> ticketCategoryCountMap =
                seatVoList.stream().collect(Collectors.groupingBy(SeatVo::getTicketCategoryId, Collectors.counting()));
        //更新票档数据集合
        JSONArray jsonArray = new JSONArray();
        ticketCategoryCountMap.forEach((k, v) -> {
            //这里是计算更新票档数据
            JSONObject jsonObject = new JSONObject();
            //票档数量的key
            jsonObject.put("programTicketRemainNumberHashKey", RedisKeyBuild.createRedisKey(
                    RedisKeyManage.PROGRAM_TICKET_REMAIN_NUMBER_HASH_RESOLUTION, programId, k).getRelKey());
            //票档id
            jsonObject.put("ticketCategoryId", String.valueOf(k));
            //如果是生成订单操作，则将扣减余票数量
            if (Objects.equals(orderStatus.getCode(), OrderStatus.NO_PAY.getCode())) {
                jsonObject.put("count", "-" + v);
                //如果是取消订单操作，则将恢复余票数量
            } else if (Objects.equals(orderStatus.getCode(), OrderStatus.CANCEL.getCode())) {
                jsonObject.put("count", v);
            }
            jsonArray.add(jsonObject);
        });
        //座位map key:票档id  value:座位集合
        Map<Long, List<SeatVo>> seatVoMap =
                seatVoList.stream().collect(Collectors.groupingBy(SeatVo::getTicketCategoryId));
        JSONArray delSeatIdjsonArray = new JSONArray();
        JSONArray addSeatDatajsonArray = new JSONArray();
        seatVoMap.forEach((k, v) -> {
            JSONObject delSeatIdjsonObject = new JSONObject();
            JSONObject seatDatajsonObject = new JSONObject();
            String seatHashKeyDel = "";
            String seatHashKeyAdd = "";
            //如果是生成订单操作，则将座位修改为锁定状态
            if (Objects.equals(orderStatus.getCode(), OrderStatus.NO_PAY.getCode())) {
                //没有售卖座位的key
                seatHashKeyDel = (RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_SEAT_NO_SOLD_RESOLUTION_HASH, programId, k).getRelKey());
                //锁定座位的key
                seatHashKeyAdd = (RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_SEAT_LOCK_RESOLUTION_HASH, programId, k).getRelKey());
                for (SeatVo seatVo : v) {
                    seatVo.setSellStatus(SellStatus.LOCK.getCode());
                }
                //如果是取消订单操作，则将座位修改为未售卖状态
            } else if (Objects.equals(orderStatus.getCode(), OrderStatus.CANCEL.getCode())) {
                //锁定座位的key
                seatHashKeyDel = (RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_SEAT_LOCK_RESOLUTION_HASH, programId, k).getRelKey());
                //没有售卖座位的key
                seatHashKeyAdd = (RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_SEAT_NO_SOLD_RESOLUTION_HASH, programId, k).getRelKey());
                for (SeatVo seatVo : v) {
                    seatVo.setSellStatus(SellStatus.NO_SOLD.getCode());
                }
            }
            //要进行删除座位的key
            delSeatIdjsonObject.put("seatHashKeyDel", seatHashKeyDel);
            delSeatIdjsonObject.put("ticketCategoryId", String.valueOf(k));
            //如果是订单创建，那么就扣除未售卖的座位id
            //如果是订单取消，那么就扣除锁定的座位id
            delSeatIdjsonObject.put("seatIdList", v.stream().map(SeatVo::getId).map(String::valueOf).collect(Collectors.toList()));
            delSeatIdjsonArray.add(delSeatIdjsonObject);
            //要进行添加座位的key
            seatDatajsonObject.put("seatHashKeyAdd", seatHashKeyAdd);
            seatDatajsonObject.put("ticketCategoryId", String.valueOf(k));
            //如果是订单创建的操作，那么添加到锁定的座位数据
            //如果是订单订单的操作，那么添加到未售卖的座位数据
            List<String> seatDataList = new ArrayList<>();
            //循环座位
            for (SeatVo seatVo : v) {
                //选放入座位did
                seatDataList.add(String.valueOf(seatVo.getId()));
                //接着放入座位对象
                seatDataList.add(JSON.toJSONString(seatVo));
            }
            //要进行添加座位的数据
            seatDatajsonObject.put("seatDataList", seatDataList);
            addSeatDatajsonArray.add(seatDatajsonObject);
        });

        //票档相关数据
        data[0] = JSON.toJSONString(jsonArray);
        //要进行删除座位的key
        data[1] = JSON.toJSONString(delSeatIdjsonArray);
        //要进行添加座位的相关数据
        data[2] = JSON.toJSONString(addSeatDatajsonArray);
        //执行lua脚本
        return programCacheResolutionOperate.programCacheOperate(keys, data);
    }
}
