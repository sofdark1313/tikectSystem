package com.tikectsystem.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tikectsystem.client.PayClient;
import com.tikectsystem.client.ProgramClient;
import com.tikectsystem.client.UserClient;
import com.tikectsystem.common.ApiResponse;
import com.tikectsystem.core.RedisKeyManage;
import com.tikectsystem.domain.DiscardOrder;
import com.tikectsystem.domain.OrderCreateDomain;
import com.tikectsystem.domain.OrderCreateMq;
import com.tikectsystem.domain.SeatIdAndTicketUserIdDomain;
import com.tikectsystem.dto.AccountOrderCountDto;
import com.tikectsystem.dto.NotifyDto;
import com.tikectsystem.dto.OrderCancelDto;
import com.tikectsystem.dto.OrderCreateDto;
import com.tikectsystem.dto.OrderCreateTestDto;
import com.tikectsystem.dto.OrderGetDto;
import com.tikectsystem.dto.OrderListDto;
import com.tikectsystem.dto.OrderPayCheckDto;
import com.tikectsystem.dto.OrderPayDto;
import com.tikectsystem.dto.OrderTicketUserCreateDto;
import com.tikectsystem.dto.PayDto;
import com.tikectsystem.dto.ProgramOperateDataDto;
import com.tikectsystem.dto.ProgramRecordTaskAddDto;
import com.tikectsystem.dto.ReduceRemainNumberDto;
import com.tikectsystem.dto.RefundDto;
import com.tikectsystem.dto.TicketCategoryCountDto;
import com.tikectsystem.dto.TradeCheckDto;
import com.tikectsystem.dto.UserGetAndTicketUserListDto;
import com.tikectsystem.entity.Order;
import com.tikectsystem.entity.OrderProgram;
import com.tikectsystem.entity.OrderTicketUser;
import com.tikectsystem.entity.OrderTicketUserAggregate;
import com.tikectsystem.entity.OrderTicketUserRecord;
import com.tikectsystem.enums.BaseCode;
import com.tikectsystem.enums.BusinessStatus;
import com.tikectsystem.enums.DiscardOrderReason;
import com.tikectsystem.enums.OrderStatus;
import com.tikectsystem.enums.PayBillStatus;
import com.tikectsystem.enums.PayChannel;
import com.tikectsystem.enums.ProgramOrderVersion;
import com.tikectsystem.enums.RecordType;
import com.tikectsystem.enums.SellStatus;
import com.tikectsystem.exception.TikectsystemFrameException;
import com.tikectsystem.mapper.OrderMapper;
import com.tikectsystem.mapper.OrderProgramMapper;
import com.tikectsystem.mapper.OrderTicketUserMapper;
import com.tikectsystem.mapper.OrderTicketUserRecordMapper;
import com.tikectsystem.redis.RedisCache;
import com.tikectsystem.redis.RedisKeyBuild;
import com.tikectsystem.repeatexecutelimit.annotion.RepeatExecuteLimit;
import com.tikectsystem.request.CustomizeRequestWrapper;
import com.tikectsystem.service.delaysend.DelayOperateProgramDataSend;
import com.tikectsystem.service.properties.OrderProperties;
import com.tikectsystem.servicelock.LockType;
import com.tikectsystem.servicelock.annotion.ServiceLock;
import com.tikectsystem.util.DateUtils;
import com.tikectsystem.util.ServiceLockTool;
import com.tikectsystem.util.StringUtil;
import com.tikectsystem.vo.AccountOrderCountVo;
import com.tikectsystem.vo.NotifyVo;
import com.tikectsystem.vo.OrderGetVo;
import com.tikectsystem.vo.OrderListVo;
import com.tikectsystem.vo.OrderPayCheckVo;
import com.tikectsystem.vo.OrderTicketInfoVo;
import com.tikectsystem.vo.SeatVo;
import com.tikectsystem.vo.TicketUserInfoVo;
import com.tikectsystem.vo.TicketUserVo;
import com.tikectsystem.vo.TradeCheckVo;
import com.tikectsystem.vo.UserAndTicketUserInfoVo;
import com.tikectsystem.vo.UserGetAndTicketUserListVo;
import com.tikectsystem.vo.UserInfoVo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.tikectsystem.constant.Constant.ALIPAY_NOTIFY_SUCCESS_RESULT;
import static com.tikectsystem.constant.Constant.GLIDE_LINE;
import static com.tikectsystem.core.DistributedLockConstants.UPDATE_ORDER_STATUS_LOCK;
import static com.tikectsystem.core.RepeatExecuteLimitConstants.CANCEL_PROGRAM_ORDER;
import static com.tikectsystem.core.RepeatExecuteLimitConstants.CREATE_PROGRAM_ORDER_MQ;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 订单 service
 * @author: 阿星不是程序员
 **/
@Slf4j
@Service
public class OrderService extends ServiceImpl<OrderMapper, Order> {
    
    @Autowired
    private UidGenerator uidGenerator;
    
    @Autowired
    private OrderMapper orderMapper;
    
    @Autowired
    private OrderTicketUserMapper orderTicketUserMapper;
    
    @Autowired
    private OrderTicketUserService orderTicketUserService;
    
    @Autowired
    private OrderTicketUserRecordService orderTicketUserRecordService;
    
    @Autowired
    private OrderProgramCacheResolutionOperate orderProgramCacheResolutionOperate;
    
    @Autowired
    private RedisCache redisCache;
    
    @Autowired
    private PayClient payClient;
    
    @Autowired
    private UserClient userClient;
    
    @Autowired
    private OrderProperties orderProperties;
    
    @Lazy
    @Autowired
    private OrderService orderService;
    
    @Autowired
    private ServiceLockTool serviceLockTool;
    
    @Autowired
    private ProgramClient programClient;
    
    @Autowired
    private OrderTicketUserRecordMapper orderTicketUserRecordMapper;
    
    @Autowired
    private OrderProgramMapper orderProgramMapper;
    
    @Autowired
    private DelayOperateProgramDataSend delayOperateProgramDataSend;

    @Transactional(rollbackFor = Exception.class)
    public String create(OrderCreateDto orderCreateDto) {
        OrderCreateDomain orderCreateDomain = new OrderCreateDomain();
        BeanUtils.copyProperties(orderCreateDto, orderCreateDomain);
        return doCreate(orderCreateDomain);
    }
    
    @Transactional(rollbackFor = Exception.class)
    public String createByMq(OrderCreateMq orderCreateMq) {
        OrderCreateDomain orderCreateDomain = new OrderCreateDomain();
        BeanUtils.copyProperties(orderCreateMq, orderCreateDomain);
        return doCreate(orderCreateDomain);
    }

    @Transactional(rollbackFor = Exception.class)
    public String doCreate(OrderCreateDomain orderCreateDomain) {
        LambdaQueryWrapper<Order> orderLambdaQueryWrapper =
                Wrappers.lambdaQuery(Order.class).eq(Order::getOrderNumber, orderCreateDomain.getOrderNumber());
        //如果订单存在了，那么直接拒绝
        Order oldOrder = orderMapper.selectOne(orderLambdaQueryWrapper);
        if (Objects.nonNull(oldOrder)) {
            throw new TikectsystemFrameException(BaseCode.ORDER_EXIST);
        }
        Order order = new Order();
        BeanUtil.copyProperties(orderCreateDomain,order);
        order.setId(uidGenerator.getUid());
        order.setDistributionMode("电子票");
        order.setTakeTicketMode("请使用购票人身份证直接入场");
        //购票人订单对象
        List<OrderTicketUser> orderTicketUserList = new ArrayList<>();
        //购票人订单记录对象
        List<OrderTicketUserRecord> orderTicketUserRecordList = new ArrayList<>();
        for (OrderTicketUserCreateDto orderTicketUserCreateDto : orderCreateDomain.getOrderTicketUserCreateDtoList()) {
            OrderTicketUser orderTicketUser = new OrderTicketUser();
            BeanUtil.copyProperties(orderTicketUserCreateDto,orderTicketUser);
            orderTicketUser.setId(uidGenerator.getUid());
            orderTicketUserList.add(orderTicketUser);

            OrderTicketUserRecord orderTicketUserRecord = new OrderTicketUserRecord();
            BeanUtil.copyProperties(orderTicketUserCreateDto,orderTicketUserRecord);
            orderTicketUserRecord.setIdentifierId(orderCreateDomain.getIdentifierId());
            orderTicketUserRecord.setTicketUserOrderId(orderTicketUser.getId());
            orderTicketUserRecord.setRecordTypeCode(RecordType.REDUCE.getCode());
            orderTicketUserRecord.setRecordTypeValue(RecordType.REDUCE.getValue());
            orderTicketUserRecordList.add(orderTicketUserRecord);
        }
        //插入主订单
        orderMapper.insert(order);
        //插入购票人订单
        orderTicketUserService.saveBatch(orderTicketUserList);
        //插入购票人订单记录
        orderTicketUserRecordService.saveBatch(orderTicketUserRecordList);
        //插入订单节目
        OrderProgram orderProgram = new OrderProgram();
        orderProgram.setId(uidGenerator.getUid());
        orderProgram.setProgramId(order.getProgramId());
        orderProgram.setOrderNumber(order.getOrderNumber());
        orderProgram.setIdentifierId(order.getIdentifierId());
        orderProgramMapper.insert(orderProgram);
        //用户下此节目的订单数量加1操作
        redisCache.incrBy(RedisKeyBuild.createRedisKey(
                RedisKeyManage.ACCOUNT_ORDER_COUNT,orderCreateDomain.getUserId(),
                orderCreateDomain.getProgramId()),orderCreateDomain.getOrderTicketUserCreateDtoList().size());
        return String.valueOf(order.getOrderNumber());
    }
    
    /**
     * 订单取消，以订单编号加锁
     * */
    @RepeatExecuteLimit(name = CANCEL_PROGRAM_ORDER,keys = {"#orderCancelDto.orderNumber"})
    @ServiceLock(name = UPDATE_ORDER_STATUS_LOCK,keys = {"#orderCancelDto.orderNumber"})
    @Transactional(rollbackFor = Exception.class)
    public boolean cancel(OrderCancelDto orderCancelDto){
        updateOrderRelatedData(orderCancelDto.getOrderNumber(),OrderStatus.CANCEL);
        return true;
    }
    
    public String pay(OrderPayDto orderPayDto) {
        Long orderNumber = orderPayDto.getOrderNumber();
        LambdaQueryWrapper<Order> orderLambdaQueryWrapper =
                Wrappers.lambdaQuery(Order.class).eq(Order::getOrderNumber, orderNumber);
        Order order = orderMapper.selectOne(orderLambdaQueryWrapper);
        if (Objects.isNull(order)) {
            throw new TikectsystemFrameException(BaseCode.ORDER_NOT_EXIST);
        }
        if (Objects.equals(order.getOrderStatus(), OrderStatus.CANCEL.getCode())) {
            throw new TikectsystemFrameException(BaseCode.ORDER_CANCEL);
        }
        if (Objects.equals(order.getOrderStatus(), OrderStatus.PAY.getCode())) {
            throw new TikectsystemFrameException(BaseCode.ORDER_PAY);
        }
        if (Objects.equals(order.getOrderStatus(), OrderStatus.REFUND.getCode())) {
            throw new TikectsystemFrameException(BaseCode.ORDER_REFUND);
        }
        if (orderPayDto.getPrice().compareTo(order.getOrderPrice()) != 0) {
            throw new TikectsystemFrameException(BaseCode.PAY_PRICE_NOT_EQUAL_ORDER_PRICE);
        }
        PayDto payDto = getPayDto(orderPayDto, orderNumber);
        ApiResponse<String> payResponse = payClient.commonPay(payDto);
        if (!Objects.equals(payResponse.getCode(), BaseCode.SUCCESS.getCode())) {
            throw new TikectsystemFrameException(payResponse);
        }
        return payResponse.getData();
    }
    
    private PayDto getPayDto(OrderPayDto orderPayDto, Long orderNumber) {
        PayDto payDto = new PayDto();
        payDto.setOrderNumber(String.valueOf(orderNumber));
        payDto.setPayBillType(orderPayDto.getPayBillType());
        payDto.setSubject(orderPayDto.getSubject());
        payDto.setChannel(orderPayDto.getChannel());
        payDto.setPlatform(orderPayDto.getPlatform());
        payDto.setPrice(orderPayDto.getPrice());
        payDto.setNotifyUrl(orderProperties.getOrderPayNotifyUrl());
        payDto.setReturnUrl(orderProperties.getOrderPayReturnUrl());
        return payDto;
    }
    
    /**
     * 支付后订单检查，以订单编号加锁，防止多次更新
     * */
    @ServiceLock(name = UPDATE_ORDER_STATUS_LOCK,keys = {"#orderPayCheckDto.orderNumber"})
    public OrderPayCheckVo payCheck(OrderPayCheckDto orderPayCheckDto){
        OrderPayCheckVo orderPayCheckVo = new OrderPayCheckVo();
        LambdaQueryWrapper<Order> orderLambdaQueryWrapper =
                Wrappers.lambdaQuery(Order.class).eq(Order::getOrderNumber, orderPayCheckDto.getOrderNumber());
        Order order = orderMapper.selectOne(orderLambdaQueryWrapper);
        if (Objects.isNull(order)) {
            throw new TikectsystemFrameException(BaseCode.ORDER_NOT_EXIST);
        }
        BeanUtil.copyProperties(order,orderPayCheckVo);

        //如果订单已取消则进行退款
        if (Objects.equals(order.getOrderStatus(), OrderStatus.CANCEL.getCode())) {
            RefundDto refundDto = new RefundDto();
            refundDto.setOrderNumber(String.valueOf(order.getOrderNumber()));
            refundDto.setAmount(order.getOrderPrice());
            refundDto.setChannel("alipay");
            refundDto.setReason("延迟订单关闭");
            ApiResponse<String> response = payClient.refund(refundDto);
            if (response.getCode().equals(BaseCode.SUCCESS.getCode())) {
                //调用支付服务退款成功后，把订单更新为已退款状态
                Order updateOrder = new Order();
                updateOrder.setEditTime(DateUtils.now());
                updateOrder.setOrderStatus(OrderStatus.REFUND.getCode());
                orderMapper.update(updateOrder,Wrappers.lambdaUpdate(Order.class).eq(Order::getOrderNumber, order.getOrderNumber()));
            }else {
                log.error("pay服务退款失败 dto : {} response : {}",JSON.toJSONString(refundDto),JSON.toJSONString(response));
            }
            orderPayCheckVo.setOrderStatus(OrderStatus.REFUND.getCode());
            orderPayCheckVo.setCancelOrderTime(DateUtils.now());
            return orderPayCheckVo;
        }

        //调用支付服务查询支付渠道的真实状态
        TradeCheckDto tradeCheckDto = new TradeCheckDto();
        tradeCheckDto.setOutTradeNo(String.valueOf(orderPayCheckDto.getOrderNumber()));
        tradeCheckDto.setChannel(Optional.ofNullable(PayChannel.getRc(orderPayCheckDto.getPayChannelType()))
                .map(PayChannel::getValue).orElseThrow(() -> new TikectsystemFrameException(BaseCode.PAY_CHANNEL_NOT_EXIST)));
        ApiResponse<TradeCheckVo> tradeCheckVoApiResponse = payClient.tradeCheck(tradeCheckDto);
        if (!Objects.equals(tradeCheckVoApiResponse.getCode(), BaseCode.SUCCESS.getCode())) {
            throw new TikectsystemFrameException(tradeCheckVoApiResponse);
        }
        TradeCheckVo tradeCheckVo = Optional.ofNullable(tradeCheckVoApiResponse.getData())
                .orElseThrow(() -> new TikectsystemFrameException(BaseCode.PAY_BILL_NOT_EXIST));
        if (tradeCheckVo.isSuccess()) {
            Integer payBillStatus = tradeCheckVo.getPayBillStatus();
            Integer orderStatus = order.getOrderStatus();
            //如果订单的状态和账单的状态不一致，说明支付的回调没有成功，那么就在这次更新数据
            if (!Objects.equals(orderStatus, payBillStatus)) {
                orderPayCheckVo.setOrderStatus(payBillStatus);
                try {
                    //如果账单的状态是支付，那么执行订单支付的操作
                    if (Objects.equals(payBillStatus, PayBillStatus.PAY.getCode())) {
                        orderPayCheckVo.setPayOrderTime(DateUtils.now());
                        orderService.updateOrderRelatedData(order.getOrderNumber(),OrderStatus.PAY);
                        //如果账单的状态是取消，那么执行订单取消的操作
                    }else if (Objects.equals(payBillStatus, PayBillStatus.CANCEL.getCode())) {
                        orderPayCheckVo.setCancelOrderTime(DateUtils.now());
                        orderService.updateOrderRelatedData(order.getOrderNumber(),OrderStatus.CANCEL);
                    }
                }catch(Exception e) {
                    log.warn("updateOrderRelatedData warn message",e);
                }
            }
        }else {
            throw new TikectsystemFrameException(BaseCode.PAY_TRADE_CHECK_ERROR);
        }
        return orderPayCheckVo;
    }


    public String alipayNotify(HttpServletRequest request){
        //将回调中的参数转为Map结构
        Map<String, String> params = new HashMap<>(256);
        if (request instanceof CustomizeRequestWrapper) {
            CustomizeRequestWrapper customizeRequestWrapper = (CustomizeRequestWrapper)request;
            String requestBody = customizeRequestWrapper.getRequestBody();
            params = StringUtil.convertQueryStringToMap(requestBody);
        }
        //获取其中的订单号
        String outTradeNo = params.get("out_trade_no");
        if (StringUtil.isEmpty(outTradeNo)) {
            return "failure";
        }
        //加锁，防止并发问题
        RLock lock = serviceLockTool.getLock(LockType.Reentrant, UPDATE_ORDER_STATUS_LOCK,
                new String[]{outTradeNo});
        lock.lock();
        try {
            Order order = orderMapper.selectOne(Wrappers.lambdaQuery(Order.class).eq(Order::getOrderNumber, outTradeNo));
            if (Objects.isNull(order)) {
                throw new TikectsystemFrameException(BaseCode.ORDER_NOT_EXIST);
            }
            //如果订单已取消则进行退款
            if (Objects.equals(order.getOrderStatus(), OrderStatus.CANCEL.getCode())) {
                RefundDto refundDto = new RefundDto();
                refundDto.setOrderNumber(outTradeNo);
                refundDto.setAmount(order.getOrderPrice());
                refundDto.setChannel("alipay");
                refundDto.setReason("延迟订单关闭");
                ApiResponse<String> response = payClient.refund(refundDto);
                if (response.getCode().equals(BaseCode.SUCCESS.getCode())) {
                    //调用支付服务退款成功后，把订单更新为已退款状态
                    Order updateOrder = new Order();
                    updateOrder.setEditTime(DateUtils.now());
                    updateOrder.setOrderStatus(OrderStatus.REFUND.getCode());
                    orderMapper.update(updateOrder,Wrappers.lambdaUpdate(Order.class).eq(Order::getOrderNumber, outTradeNo));
                }else {
                    log.error("pay服务退款失败 dto : {} response : {}",JSON.toJSONString(refundDto),JSON.toJSONString(response));
                }
                return ALIPAY_NOTIFY_SUCCESS_RESULT;
            }

            NotifyDto notifyDto = new NotifyDto();
            notifyDto.setChannel(PayChannel.ALIPAY.getValue());
            notifyDto.setParams(params);
            //调用支付服务，将回调中的参数进行签名验证
            ApiResponse<NotifyVo> notifyResponse = payClient.notify(notifyDto);
            if (!Objects.equals(notifyResponse.getCode(), BaseCode.SUCCESS.getCode())) {
                throw new TikectsystemFrameException(notifyResponse);
            }
            //如果验证过程的话则进行订单状态更新
            if (ALIPAY_NOTIFY_SUCCESS_RESULT.equals(notifyResponse.getData().getPayResult())) {
                try {
                    orderService.updateOrderRelatedData(Long.parseLong(notifyResponse.getData().getOutTradeNo())
                            ,OrderStatus.PAY);
                }catch (Exception e) {
                    log.warn("updateOrderRelatedData warn message",e);
                }
            }
            return notifyResponse.getData().getPayResult();
        }finally {
            lock.unlock();
        }

    }
    
    /**
     * 更新订单和购票人订单状态以及操作缓存数据
     * */
    @Transactional(rollbackFor = Exception.class)
    public void updateOrderRelatedData(Long orderNumber,OrderStatus orderStatus){
        //如果不是取消或者支付操作，则直接抛出异常提示
        if (!(Objects.equals(orderStatus.getCode(), OrderStatus.CANCEL.getCode()) ||
                Objects.equals(orderStatus.getCode(), OrderStatus.PAY.getCode()))) {
            throw new TikectsystemFrameException(  BaseCode.OPERATE_ORDER_STATUS_NOT_PERMIT);
        }
        //查询订单
        LambdaQueryWrapper<Order> orderLambdaQueryWrapper =
                Wrappers.lambdaQuery(Order.class).eq(Order::getOrderNumber, orderNumber);
        Order order = orderMapper.selectOne(orderLambdaQueryWrapper);
        //检查订单的状态 已取消、已支付、已退单的状态不再执行
        checkOrderStatus(order);
        //查询该订单下的购票人订单列表
        LambdaQueryWrapper<OrderTicketUser> orderTicketUserLambdaQueryWrapper =
                Wrappers.lambdaQuery(OrderTicketUser.class).eq(OrderTicketUser::getOrderNumber, order.getOrderNumber());
        List<OrderTicketUser> orderTicketUserList = orderTicketUserMapper.selectList(orderTicketUserLambdaQueryWrapper);
        if (CollectionUtil.isEmpty(orderTicketUserList)) {
            throw new TikectsystemFrameException(BaseCode.TICKET_USER_ORDER_NOT_EXIST);
        }
        //将订单更新为取消或者支付状态
        Order updateOrder = new Order();
        updateOrder.setId(order.getId());
        updateOrder.setOrderStatus(orderStatus.getCode());
        //将购票人订单更新为取消或者支付状态
        OrderTicketUser updateOrderTicketUser = new OrderTicketUser();
        updateOrderTicketUser.setOrderStatus(orderStatus.getCode());

        //如果是支付的话，那么记录的类型就是改变状态
        //记录类型code
        Integer recordTypeCode = RecordType.CHANGE_STATUS.getCode();
        //记录类型值
        String recordTypeValue = RecordType.CHANGE_STATUS.getValue();
        //支付状态的操作
        if (Objects.equals(orderStatus.getCode(), OrderStatus.PAY.getCode())) {
            updateOrder.setPayOrderTime(DateUtils.now());
            updateOrderTicketUser.setPayOrderTime(DateUtils.now());
        } else if (Objects.equals(orderStatus.getCode(), OrderStatus.CANCEL.getCode())) {
            //取消状态的操作
            updateOrder.setCancelOrderTime(DateUtils.now());
            updateOrderTicketUser.setCancelOrderTime(DateUtils.now());
            //如果是取消的话，那么记录的类型就是增加余票
            recordTypeCode = RecordType.INCREASE.getCode();
            recordTypeValue = RecordType.INCREASE.getValue();
        }
        //更新订单
        LambdaUpdateWrapper<Order> orderLambdaUpdateWrapper =
                Wrappers.lambdaUpdate(Order.class).eq(Order::getOrderNumber, order.getOrderNumber());
        int updateOrderResult = orderMapper.update(updateOrder,orderLambdaUpdateWrapper);
        //更新购票人订单
        LambdaUpdateWrapper<OrderTicketUser> orderTicketUserLambdaUpdateWrapper =
                Wrappers.lambdaUpdate(OrderTicketUser.class).eq(OrderTicketUser::getOrderNumber, order.getOrderNumber());
        int updateTicketUserOrderResult =
                orderTicketUserMapper.update(updateOrderTicketUser,orderTicketUserLambdaUpdateWrapper);
        if (updateOrderResult <= 0 || updateTicketUserOrderResult <= 0) {
            throw new TikectsystemFrameException(BaseCode.ORDER_CANAL_ERROR);
        }
        List<SeatIdAndTicketUserIdDomain> seatIdAndTicketUserIdDomainList = new ArrayList<>();
        List<OrderTicketUserRecord> orderTicketUserRecordList = new ArrayList<>();
        for (OrderTicketUser orderTicketUser : orderTicketUserList) {
            //购票人订单记录
            OrderTicketUserRecord orderTicketUserRecord = new OrderTicketUserRecord();
            BeanUtils.copyProperties(orderTicketUser,orderTicketUserRecord);
            orderTicketUserRecord.setId(uidGenerator.getUid());
            orderTicketUserRecord.setIdentifierId(order.getIdentifierId());
            orderTicketUserRecord.setTicketUserOrderId(orderTicketUser.getId());
            orderTicketUserRecord.setRecordTypeCode(recordTypeCode);
            orderTicketUserRecord.setRecordTypeValue(recordTypeValue);
            orderTicketUserRecordList.add(orderTicketUserRecord);
            //购票人订单id和座位id
            seatIdAndTicketUserIdDomainList.add(new SeatIdAndTicketUserIdDomain(orderTicketUser.getSeatId(),
                    orderTicketUser.getTicketUserId()));
        }
        //添加购票人订单记录流水
        orderTicketUserRecordService.saveBatch(orderTicketUserRecordList);
        
        //如果是取消操作，那么把用户下该节目的订单数量要-1
        if (Objects.equals(orderStatus.getCode(), OrderStatus.CANCEL.getCode())) {
            redisCache.incrBy(RedisKeyBuild.createRedisKey(
                    RedisKeyManage.ACCOUNT_ORDER_COUNT,order.getUserId(),order.getProgramId()),-updateTicketUserOrderResult);
        }
        Long programId = order.getProgramId();
        //将购票人订单集合转换成map结构，key：票档id value：购票人订单
        Map<Long, List<OrderTicketUser>> orderTicketUserSeatList = 
                orderTicketUserList.stream().collect(Collectors.groupingBy(OrderTicketUser::getTicketCategoryId));
        Map<Long,List<Long>> seatMap = new HashMap<>(orderTicketUserSeatList.size());
        //根据orderTicketUserSeatList得到seatMap
        //seatMap结构 key：票档id  value：座位id集合
        orderTicketUserSeatList.forEach((k,v) -> {
            seatMap.put(k,v.stream().map(OrderTicketUser::getSeatId).collect(Collectors.toList()));
        });
        //更新缓存和节目库相关数据
        updateProgramRelatedDataResolution(programId,seatMap,orderStatus,order.getIdentifierId(),order.getUserId(),
                seatIdAndTicketUserIdDomainList,order.getOrderVersion());
    }
    
    public void checkOrderStatus(Order order){
        if (Objects.isNull(order)) {
            throw new TikectsystemFrameException(BaseCode.ORDER_NOT_EXIST);
        }
        if (Objects.equals(order.getOrderStatus(), OrderStatus.CANCEL.getCode())) {
            throw new TikectsystemFrameException(BaseCode.ORDER_CANCEL);
        }
        if (Objects.equals(order.getOrderStatus(), OrderStatus.PAY.getCode())) {
            throw new TikectsystemFrameException(BaseCode.ORDER_PAY);
        }
        if (Objects.equals(order.getOrderStatus(), OrderStatus.REFUND.getCode())) {
            throw new TikectsystemFrameException(BaseCode.ORDER_REFUND);
        }
    }
    
    public void updateProgramRelatedDataResolution(Long programId,Map<Long,List<Long>> seatMap, OrderStatus orderStatus,Long identifierId, Long userId,
                                                   List<SeatIdAndTicketUserIdDomain> seatIdAndTicketUserIdDomainList,
                                                   Integer orderVersion){
        Map<Long, List<SeatVo>> seatVoMap = new HashMap<>(seatMap.size());
        seatMap.forEach((k,v) -> {
            seatVoMap.put(k,redisCache.multiGetForHash(
                    RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_SEAT_LOCK_RESOLUTION_HASH, programId, k),
                    v.stream().map(String::valueOf).collect(Collectors.toList()), SeatVo.class));
        });
        if (CollectionUtil.isEmpty(seatVoMap)) {
            throw new TikectsystemFrameException(BaseCode.LOCK_SEAT_LIST_EMPTY);
        }
        JSONArray jsonArray = new JSONArray();
        JSONArray addSeatDatajsonArray = new JSONArray();
        List<TicketCategoryCountDto> ticketCategoryCountDtoList = new ArrayList<>(seatVoMap.size());
        JSONArray unLockSeatIdjsonArray = new JSONArray();
        List<Long> unLockSeatIdList = new ArrayList<>();
        seatVoMap.forEach((k,v) -> {
            JSONObject unLockSeatIdjsonObject = new JSONObject();
            unLockSeatIdjsonObject.put("programSeatLockHashKey", RedisKeyBuild.createRedisKey(
                    RedisKeyManage.PROGRAM_SEAT_LOCK_RESOLUTION_HASH, programId, k).getRelKey());
            unLockSeatIdjsonObject.put("unLockSeatIdList",v.stream()
                    .map(SeatVo::getId).map(String::valueOf).collect(Collectors.toList()));
            unLockSeatIdjsonArray.add(unLockSeatIdjsonObject);
            JSONObject seatDatajsonObject = new JSONObject();
            String seatHashKeyAdd = "";
            if (Objects.equals(orderStatus.getCode(), OrderStatus.CANCEL.getCode())) {
                seatHashKeyAdd = RedisKeyBuild.createRedisKey(
                        RedisKeyManage.PROGRAM_SEAT_NO_SOLD_RESOLUTION_HASH, programId, k).getRelKey();
                for (SeatVo seatVo : v) {
                    //座位状态要改成未售卖
                    seatVo.setSellStatus(SellStatus.NO_SOLD.getCode());
                }
            }else if (Objects.equals(orderStatus.getCode(), OrderStatus.PAY.getCode())) {
                seatHashKeyAdd = RedisKeyBuild.createRedisKey(
                        RedisKeyManage.PROGRAM_SEAT_SOLD_RESOLUTION_HASH, programId, k).getRelKey();
                for (SeatVo seatVo : v) {
                    seatVo.setSellStatus(SellStatus.SOLD.getCode());
                }
            }
            seatDatajsonObject.put("seatHashKeyAdd",seatHashKeyAdd);
            List<String> seatDataList = new ArrayList<>();
            for (SeatVo seatVo : v) {
                seatDataList.add(String.valueOf(seatVo.getId()));
                seatDataList.add(JSON.toJSONString(seatVo));
            }
            seatDatajsonObject.put("seatDataList",seatDataList);
            addSeatDatajsonArray.add(seatDatajsonObject);
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("programTicketRemainNumberHashKey",RedisKeyBuild.createRedisKey(
                    RedisKeyManage.PROGRAM_TICKET_REMAIN_NUMBER_HASH_RESOLUTION, programId, k).getRelKey());
            jsonObject.put("ticketCategoryId",String.valueOf(k));
            jsonObject.put("count",v.size());
            jsonArray.add(jsonObject);
            TicketCategoryCountDto ticketCategoryCountDto = new TicketCategoryCountDto();
            ticketCategoryCountDto.setTicketCategoryId(k);
            ticketCategoryCountDto.setCount((long) v.size());
            ticketCategoryCountDtoList.add(ticketCategoryCountDto);
            
            unLockSeatIdList.addAll(v.stream().map(SeatVo::getId).toList());
        });
        List<String> keys = new ArrayList<>();
        keys.add(String.valueOf(orderStatus.getCode()));
        keys.add(String.valueOf(programId));
        keys.add(RedisKeyBuild.getRedisKey(RedisKeyManage.PROGRAM_RECORD));
        
        String recordTye = Objects.equals(orderStatus.getCode(), OrderStatus.CANCEL.getCode()) ? RecordType.INCREASE.getValue() : RecordType.CHANGE_STATUS.getValue();
        keys.add(recordTye + GLIDE_LINE + identifierId + GLIDE_LINE + userId);
        keys.add(recordTye);
        Object[] data = new String[4];
        data[0] = JSON.toJSONString(unLockSeatIdjsonArray);
        data[1] = JSON.toJSONString(addSeatDatajsonArray);
        data[2] = JSON.toJSONString(jsonArray);
        data[3] = JSON.toJSONString(seatIdAndTicketUserIdDomainList);
        
        ProgramOperateDataDto programOperateDataDto = new ProgramOperateDataDto();
        programOperateDataDto.setProgramId(programId);
        programOperateDataDto.setSeatIdList(unLockSeatIdList);
        programOperateDataDto.setTicketCategoryCountDtoList(ticketCategoryCountDtoList);
        programOperateDataDto.setOrderVersion(orderVersion);
        //如果创建订单版本是v1，v2，v3
        if (!orderVersion.equals(ProgramOrderVersion.V4_VERSION.getValue())){
            orderProgramCacheResolutionOperate.programCacheReverseOperate(keys,data);
            if (Objects.equals(orderStatus.getCode(), OrderStatus.PAY.getCode())) {
                programOperateDataDto.setSellStatus(SellStatus.SOLD.getCode());
                delayOperateProgramDataSend.sendMessage(JSON.toJSONString(programOperateDataDto));
            }
        }else {
            //如果创建订单版本是v4 更新节目服务的相关数据
            if (Objects.equals(orderStatus.getCode(), OrderStatus.PAY.getCode()) ||
                    Objects.equals(orderStatus.getCode(), OrderStatus.CANCEL.getCode())) {
                programOperateDataDto.setSellStatus(Objects.equals(orderStatus.getCode(), OrderStatus.PAY.getCode()) ? SellStatus.SOLD.getCode() : SellStatus.NO_SOLD.getCode());
                ApiResponse<Boolean> programApiResponse = programClient.operateProgramData(programOperateDataDto);
                if (!Objects.equals(programApiResponse.getCode(), BaseCode.SUCCESS.getCode())) {
                    throw new TikectsystemFrameException(programApiResponse);
                }
            }
            orderProgramCacheResolutionOperate.programCacheReverseOperate(keys,data);
        }
    }

    public List<OrderListVo> selectList(OrderListDto orderListDto) {
        List<OrderListVo> orderListVos = new ArrayList<>();
        LambdaQueryWrapper<Order> orderLambdaQueryWrapper =
                Wrappers.lambdaQuery(Order.class).eq(Order::getUserId, orderListDto.getUserId());
        //查询主订单列表
        List<Order> orderList = orderMapper.selectList(orderLambdaQueryWrapper);
        if (CollectionUtil.isEmpty(orderList)) {
            return orderListVos;
        }
        orderListVos = BeanUtil.copyToList(orderList, OrderListVo.class);
        //每个订单下的购票人订单数量统计
        List<OrderTicketUserAggregate> orderTicketUserAggregateList =
                orderTicketUserMapper.selectOrderTicketUserAggregate(orderList.stream().map(Order::getOrderNumber).
                        collect(Collectors.toList()));
        Map<Long, Integer> orderTicketUserAggregateMap = orderTicketUserAggregateList.stream()
                .collect(Collectors.toMap(OrderTicketUserAggregate::getOrderNumber,
                        OrderTicketUserAggregate::getOrderTicketUserCount, (v1, v2) -> v2));
        for (OrderListVo orderListVo : orderListVos) {
            orderListVo.setTicketCount(orderTicketUserAggregateMap.get(orderListVo.getOrderNumber()));
        }
        return orderListVos;
    }

    public OrderGetVo get(OrderGetDto orderGetDto) {
        //查询订单
        LambdaQueryWrapper<Order> orderLambdaQueryWrapper =
                Wrappers.lambdaQuery(Order.class).eq(Order::getOrderNumber, orderGetDto.getOrderNumber());
        Order order = orderMapper.selectOne(orderLambdaQueryWrapper);
        if (Objects.isNull(order)) {
            throw new TikectsystemFrameException(BaseCode.ORDER_NOT_EXIST);
        }
        //查询购票人订单
        LambdaQueryWrapper<OrderTicketUser> orderTicketUserLambdaQueryWrapper =
                Wrappers.lambdaQuery(OrderTicketUser.class).eq(OrderTicketUser::getOrderNumber, order.getOrderNumber());
        List<OrderTicketUser> orderTicketUserList = orderTicketUserMapper.selectList(orderTicketUserLambdaQueryWrapper);
        if (CollectionUtil.isEmpty(orderTicketUserList)) {
            throw new TikectsystemFrameException(BaseCode.TICKET_USER_ORDER_NOT_EXIST);
        }

        OrderGetVo orderGetVo = new OrderGetVo();
        BeanUtil.copyProperties(order,orderGetVo);

        //组装购票订单信息
        List<OrderTicketInfoVo> orderTicketInfoVoList = new ArrayList<>();
        //按照购票订单的金额进行分组
        Map<BigDecimal, List<OrderTicketUser>> orderTicketUserMap =
                orderTicketUserList.stream().collect(Collectors.groupingBy(OrderTicketUser::getOrderPrice));
        orderTicketUserMap.forEach((k,v) -> {
            OrderTicketInfoVo orderTicketInfoVo = new OrderTicketInfoVo();
            String seatInfo = "暂无座位信息";
            //如果节目是允许选座的，才显示出当时生成订单时产生的座位信息
            if (order.getProgramPermitChooseSeat().equals(BusinessStatus.YES.getCode())) {
                seatInfo = v.stream().map(OrderTicketUser::getSeatInfo).collect(Collectors.joining(","));
            }
            orderTicketInfoVo.setSeatInfo(seatInfo);
            orderTicketInfoVo.setPrice(v.get(0).getOrderPrice());
            orderTicketInfoVo.setQuantity(v.size());
            orderTicketInfoVo.setRelPrice(v.stream().map(OrderTicketUser::getOrderPrice)
                    .reduce(BigDecimal.ZERO,BigDecimal::add));
            orderTicketInfoVoList.add(orderTicketInfoVo);
        });

        orderGetVo.setOrderTicketInfoVoList(orderTicketInfoVoList);

        //查询用户和购票人信息
        UserGetAndTicketUserListDto userGetAndTicketUserListDto = new UserGetAndTicketUserListDto();
        userGetAndTicketUserListDto.setUserId(order.getUserId());
        ApiResponse<UserGetAndTicketUserListVo> userGetAndTicketUserApiResponse =
                userClient.getUserAndTicketUserList(userGetAndTicketUserListDto);

        if (!Objects.equals(userGetAndTicketUserApiResponse.getCode(), BaseCode.SUCCESS.getCode())) {
            throw new TikectsystemFrameException(userGetAndTicketUserApiResponse);

        }
        //验证用户和购票人信息是否存在
        UserGetAndTicketUserListVo userAndTicketUserListVo =
                Optional.ofNullable(userGetAndTicketUserApiResponse.getData())
                        .orElseThrow(() -> new TikectsystemFrameException(BaseCode.RPC_RESULT_DATA_EMPTY));
        //如果用户信息空，抛出异常
        if (Objects.isNull(userAndTicketUserListVo.getUserVo())) {
            throw new TikectsystemFrameException(BaseCode.USER_EMPTY);
        }
        //如果购票人信息空，抛出异常
        if (CollectionUtil.isEmpty(userAndTicketUserListVo.getTicketUserVoList())) {
            throw new TikectsystemFrameException(BaseCode.TICKET_USER_EMPTY);
        }
        //从查询得到的购票人信息中进行过滤出该订单下购票人的信息
        List<TicketUserVo> filterTicketUserVoList = new ArrayList<>();
        Map<Long, TicketUserVo> ticketUserVoMap = userAndTicketUserListVo.getTicketUserVoList()
                .stream().collect(Collectors.toMap(TicketUserVo::getId, ticketUserVo -> ticketUserVo, (v1, v2) -> v2));
        for (OrderTicketUser orderTicketUser : orderTicketUserList) {
            filterTicketUserVoList.add(ticketUserVoMap.get(orderTicketUser.getTicketUserId()));
        }
        //组装数据
        UserInfoVo userInfoVo = new UserInfoVo();
        BeanUtil.copyProperties(userAndTicketUserListVo.getUserVo(),userInfoVo);
        UserAndTicketUserInfoVo userAndTicketUserInfoVo = new UserAndTicketUserInfoVo();
        userAndTicketUserInfoVo.setUserInfoVo(userInfoVo);
        userAndTicketUserInfoVo.setTicketUserInfoVoList(BeanUtil.copyToList(filterTicketUserVoList, TicketUserInfoVo.class));
        orderGetVo.setUserAndTicketUserInfoVo(userAndTicketUserInfoVo);

        return orderGetVo;
    }
    
    public AccountOrderCountVo accountOrderCount(AccountOrderCountDto accountOrderCountDto) {
        AccountOrderCountVo accountOrderCountVo = new AccountOrderCountVo();
        accountOrderCountVo.setCount(orderMapper.accountOrderCount(accountOrderCountDto.getUserId(),
                accountOrderCountDto.getProgramId()));
        return accountOrderCountVo;
    }
    
    
    @RepeatExecuteLimit(name = CREATE_PROGRAM_ORDER_MQ,keys = {"#orderCreateMq.orderNumber"})
    @Transactional(rollbackFor = Exception.class)
    public String createMq(OrderCreateMq orderCreateMq){
        List<OrderTicketUserCreateDto> orderTicketUserCreateDtoList = orderCreateMq.getOrderTicketUserCreateDtoList();
        //使用 Stream API 按 ticketCategoryId 分组并计数
        Map<Long, Long> countMap = orderTicketUserCreateDtoList.stream()
                .collect(Collectors.groupingBy(OrderTicketUserCreateDto::getTicketCategoryId, Collectors.counting()));
        
        //将统计结果转换为列表，存入 TicketCountDto 对象中
        List<TicketCategoryCountDto> ticketCountList = countMap.entrySet().stream()
                .map(entry -> new TicketCategoryCountDto(entry.getKey(), entry.getValue()))
                .toList();
        //修改节目服务中的座位状态和扣减库存
        ReduceRemainNumberDto reduceRemainNumberDto = new ReduceRemainNumberDto();
        reduceRemainNumberDto.setProgramId(orderCreateMq.getProgramId());
        reduceRemainNumberDto.setSellStatus(SellStatus.LOCK.getCode());
        reduceRemainNumberDto.setSeatIdList(orderTicketUserCreateDtoList.stream().map(OrderTicketUserCreateDto::getSeatId).collect(Collectors.toList()));
        reduceRemainNumberDto.setTicketCategoryCountDtoList(ticketCountList);
        ApiResponse<Boolean> programApiResponse = programClient.operateSeatLockAndTicketCategoryRemainNumber(reduceRemainNumberDto);
        if (!Objects.equals(programApiResponse.getCode(), BaseCode.SUCCESS.getCode())) {
            //将因为修改节目服务余票和座位失败，导致丢弃的订单放入redis中
            redisCache.leftPushForList(RedisKeyBuild.createRedisKey(RedisKeyManage.DISCARD_ORDER,
                    orderCreateMq.getProgramId()),new DiscardOrder(orderCreateMq, DiscardOrderReason.MODIFY_PROGRAM_REMAIN_NUMBER_SEAT_FAIL.getCode()));
            throw new TikectsystemFrameException(programApiResponse);
        }
        //真正地创建订单
        String orderNumber = createByMq(orderCreateMq);
        redisCache.set(RedisKeyBuild.createRedisKey(RedisKeyManage.ORDER_MQ,orderNumber),orderNumber,1, TimeUnit.MINUTES);
        return orderNumber;
    }
    
    public String getCache(OrderGetDto orderGetDto) {
        return redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.ORDER_MQ,orderGetDto.getOrderNumber()),String.class);
    }
    
    @RepeatExecuteLimit(name = CANCEL_PROGRAM_ORDER,keys = {"#orderCancelDto.orderNumber"})
    @ServiceLock(name = UPDATE_ORDER_STATUS_LOCK,keys = {"#orderCancelDto.orderNumber"})
    @Transactional(rollbackFor = Exception.class)
    public boolean initiateCancel(OrderCancelDto orderCancelDto){
        Order order = orderMapper.selectOne(Wrappers.lambdaQuery(Order.class)
                .eq(Order::getOrderNumber, orderCancelDto.getOrderNumber()));
        if (Objects.isNull(order)) {
            throw new TikectsystemFrameException(BaseCode.ORDER_NOT_EXIST);
        }
        if (!Objects.equals(order.getOrderStatus(), OrderStatus.NO_PAY.getCode())) {
            throw new TikectsystemFrameException(BaseCode.CAN_NOT_CANCEL);
        }
        return cancel(orderCancelDto);
    }
    
    
    public void delOrderAndOrderTicketUser(){
        orderMapper.relDelOrder();
        orderTicketUserMapper.relDelOrderTicketUser();
        orderTicketUserRecordMapper.relDelOrderTicketUserRecord();
        orderProgramMapper.relDelOrderProgram();
    }
    @Transactional(rollbackFor = Exception.class)
    public boolean test(OrderCreateTestDto orderCreateTestDto){
        long orderNumber = uidGenerator.getOrderNumber(orderCreateTestDto.getUserId(), 2);
        OrderTicketUserRecord orderTicketUserRecord = new OrderTicketUserRecord();
        orderTicketUserRecord.setId(uidGenerator.getUid());
        orderTicketUserRecord.setOrderNumber(orderNumber);
        orderTicketUserRecord.setTicketUserOrderId(uidGenerator.getUid());
        orderTicketUserRecord.setProgramId(orderCreateTestDto.getProgramId());
        orderTicketUserRecord.setUserId(orderCreateTestDto.getUserId());
        orderTicketUserRecord.setTicketUserId(uidGenerator.getUid());
        orderTicketUserRecord.setSeatId(1L);
        orderTicketUserRecord.setIdentifierId(uidGenerator.getUid());
        orderTicketUserRecordMapper.insert(orderTicketUserRecord);
        
        ProgramRecordTaskAddDto programRecordTaskAddDto = new ProgramRecordTaskAddDto();
        programRecordTaskAddDto.setProgramId(orderCreateTestDto.getProgramId());
        programClient.add(programRecordTaskAddDto);
        
        if ("1".equals(orderCreateTestDto.getHaveException())) {
            throw new TikectsystemFrameException("模拟异常");
        }
        return true;
    }
}
