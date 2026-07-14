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
import com.tikectsystem.dto.DelayOrderCancelDto;
import com.tikectsystem.dto.OrderCancelDto;
import com.tikectsystem.dto.OrderCreateDto;
import com.tikectsystem.dto.OrderGetDto;
import com.tikectsystem.dto.OrderListDto;
import com.tikectsystem.dto.OrderPayCheckDto;
import com.tikectsystem.dto.OrderPayDto;
import com.tikectsystem.dto.OrderRequestResultUpdateDto;
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
import com.tikectsystem.service.delaysend.OrderDelayOrderCancelSend;
import com.tikectsystem.service.properties.OrderProperties;
import com.tikectsystem.servicelock.LockType;
import com.tikectsystem.servicelock.annotion.ServiceLock;
import com.tikectsystem.threadlocal.BaseParameterHolder;
import com.tikectsystem.util.DateUtils;
import com.tikectsystem.util.ServiceLockTool;
import com.tikectsystem.util.StringUtil;
import com.tikectsystem.vo.AccountOrderCountVo;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.tikectsystem.constant.Constant.ALIPAY_NOTIFY_FAILURE_RESULT;
import static com.tikectsystem.constant.Constant.ALIPAY_NOTIFY_SUCCESS_RESULT;
import static com.tikectsystem.constant.Constant.GLIDE_LINE;
import static com.tikectsystem.constant.Constant.USER_ID;
import static com.tikectsystem.core.DistributedLockConstants.UPDATE_ORDER_STATUS_LOCK;
import static com.tikectsystem.core.RepeatExecuteLimitConstants.CANCEL_PROGRAM_ORDER;
import static com.tikectsystem.core.RepeatExecuteLimitConstants.CREATE_PROGRAM_ORDER_MQ;

/**
 * @program: 鏋佸害鐪熷疄杩樺師澶ч害缃戦珮骞跺彂瀹炴垬椤圭洰銆?娣诲姞 闃挎槦涓嶆槸绋嬪簭鍛?寰俊锛屾坊鍔犳椂澶囨敞 澶ч害 鏉ヨ幏鍙栭」鐩殑瀹屾暣璧勬枡
 * @description: 璁㈠崟 service
 * @author: 闃挎槦涓嶆槸绋嬪簭鍛?
 **/
@Slf4j
@Service
public class OrderService extends ServiceImpl<OrderMapper, Order> {

    private static final String SIMPLE_PAY_RESULT = "PAY_SUCCESS";

    private static final String ORDER_REQUEST_RESULT_PROCESSING = "PROCESSING";

    private static final String ORDER_REQUEST_RESULT_RESERVED = "RESERVED";

    private static final String ORDER_REQUEST_RESULT_ORDER_CREATED = "ORDER_CREATED";

    private static final String ORDER_REQUEST_RESULT_FAILED = "FAILED";

    private static final String ORDER_REQUEST_RESULT_CANCELLED = "CANCELLED";

    private static final String ORDER_REQUEST_RESULT_EXPIRED = "EXPIRED";

    private static final long TICKET_USER_ID_ROUNDING_TOLERANCE = 1024L;

    @Autowired
    private UidGenerator uidGenerator;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderTicketUserMapper orderTicketUserMapper;

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

    @Autowired
    private OrderDelayOrderCancelSend orderDelayOrderCancelSend;

    @Transactional(rollbackFor = Exception.class)
    public String create(OrderCreateDto orderCreateDto) {
        return doCreate(buildOrderCreateDomain(orderCreateDto));
    }

    @Transactional(rollbackFor = Exception.class)
    public String createByMq(OrderCreateMq orderCreateMq) {
        return doCreate(buildOrderCreateDomain(orderCreateMq));
    }

    private OrderCreateDomain buildOrderCreateDomain(OrderCreateDto orderCreateDto) {
        OrderCreateDomain orderCreateDomain = new OrderCreateDomain();
        orderCreateDomain.setOrderNumber(orderCreateDto.getOrderNumber());
        orderCreateDomain.setProgramId(orderCreateDto.getProgramId());
        orderCreateDomain.setProgramItemPicture(orderCreateDto.getProgramItemPicture());
        orderCreateDomain.setUserId(orderCreateDto.getUserId());
        orderCreateDomain.setProgramTitle(orderCreateDto.getProgramTitle());
        orderCreateDomain.setProgramPlace(orderCreateDto.getProgramPlace());
        orderCreateDomain.setProgramShowTime(orderCreateDto.getProgramShowTime());
        orderCreateDomain.setProgramPermitChooseSeat(orderCreateDto.getProgramPermitChooseSeat());
        orderCreateDomain.setDistributionMode(orderCreateDto.getDistributionMode());
        orderCreateDomain.setTakeTicketMode(orderCreateDto.getTakeTicketMode());
        orderCreateDomain.setOrderPrice(orderCreateDto.getOrderPrice());
        orderCreateDomain.setCreateOrderTime(orderCreateDto.getCreateOrderTime());
        orderCreateDomain.setOrderTicketUserCreateDtoList(orderCreateDto.getOrderTicketUserCreateDtoList());
        orderCreateDomain.setOrderVersion(orderCreateDto.getOrderVersion());
        return orderCreateDomain;
    }

    private OrderCreateDomain buildOrderCreateDomain(OrderCreateMq orderCreateMq) {
        OrderCreateDomain orderCreateDomain = new OrderCreateDomain();
        orderCreateDomain.setIdentifierId(orderCreateMq.getIdentifierId());
        orderCreateDomain.setOrderNumber(orderCreateMq.getOrderNumber());
        orderCreateDomain.setProgramId(orderCreateMq.getProgramId());
        orderCreateDomain.setProgramItemPicture(orderCreateMq.getProgramItemPicture());
        orderCreateDomain.setUserId(orderCreateMq.getUserId());
        orderCreateDomain.setProgramTitle(orderCreateMq.getProgramTitle());
        orderCreateDomain.setProgramPlace(orderCreateMq.getProgramPlace());
        orderCreateDomain.setProgramShowTime(orderCreateMq.getProgramShowTime());
        orderCreateDomain.setProgramPermitChooseSeat(orderCreateMq.getProgramPermitChooseSeat());
        orderCreateDomain.setDistributionMode(orderCreateMq.getDistributionMode());
        orderCreateDomain.setTakeTicketMode(orderCreateMq.getTakeTicketMode());
        orderCreateDomain.setOrderPrice(orderCreateMq.getOrderPrice());
        orderCreateDomain.setCreateOrderTime(orderCreateMq.getCreateOrderTime());
        orderCreateDomain.setOrderTicketUserCreateDtoList(orderCreateMq.getOrderTicketUserCreateDtoList());
        orderCreateDomain.setOrderVersion(orderCreateMq.getOrderVersion());
        return orderCreateDomain;
    }

    private Order buildOrder(OrderCreateDomain orderCreateDomain) {
        Order order = new Order();
        order.setIdentifierId(orderCreateDomain.getIdentifierId());
        order.setOrderNumber(orderCreateDomain.getOrderNumber());
        order.setProgramId(orderCreateDomain.getProgramId());
        order.setProgramItemPicture(orderCreateDomain.getProgramItemPicture());
        order.setUserId(orderCreateDomain.getUserId());
        order.setProgramTitle(orderCreateDomain.getProgramTitle());
        order.setProgramPlace(orderCreateDomain.getProgramPlace());
        order.setProgramShowTime(orderCreateDomain.getProgramShowTime());
        order.setProgramPermitChooseSeat(orderCreateDomain.getProgramPermitChooseSeat());
        order.setDistributionMode(orderCreateDomain.getDistributionMode());
        order.setTakeTicketMode(orderCreateDomain.getTakeTicketMode());
        order.setOrderPrice(orderCreateDomain.getOrderPrice());
        order.setCreateOrderTime(orderCreateDomain.getCreateOrderTime());
        order.setOrderVersion(orderCreateDomain.getOrderVersion());
        return order;
    }

    private OrderTicketUser buildOrderTicketUser(OrderTicketUserCreateDto orderTicketUserCreateDto) {
        OrderTicketUser orderTicketUser = new OrderTicketUser();
        orderTicketUser.setOrderNumber(orderTicketUserCreateDto.getOrderNumber());
        orderTicketUser.setProgramId(orderTicketUserCreateDto.getProgramId());
        orderTicketUser.setUserId(orderTicketUserCreateDto.getUserId());
        orderTicketUser.setTicketUserId(orderTicketUserCreateDto.getTicketUserId());
        orderTicketUser.setSeatId(orderTicketUserCreateDto.getSeatId());
        orderTicketUser.setSeatInfo(orderTicketUserCreateDto.getSeatInfo());
        orderTicketUser.setTicketCategoryId(orderTicketUserCreateDto.getTicketCategoryId());
        orderTicketUser.setOrderPrice(orderTicketUserCreateDto.getOrderPrice());
        orderTicketUser.setCreateOrderTime(orderTicketUserCreateDto.getCreateOrderTime());
        return orderTicketUser;
    }

    private OrderTicketUserRecord buildOrderTicketUserRecord(OrderTicketUserCreateDto orderTicketUserCreateDto) {
        OrderTicketUserRecord orderTicketUserRecord = new OrderTicketUserRecord();
        orderTicketUserRecord.setOrderNumber(orderTicketUserCreateDto.getOrderNumber());
        orderTicketUserRecord.setProgramId(orderTicketUserCreateDto.getProgramId());
        orderTicketUserRecord.setUserId(orderTicketUserCreateDto.getUserId());
        orderTicketUserRecord.setTicketUserId(orderTicketUserCreateDto.getTicketUserId());
        orderTicketUserRecord.setSeatId(orderTicketUserCreateDto.getSeatId());
        orderTicketUserRecord.setSeatInfo(orderTicketUserCreateDto.getSeatInfo());
        orderTicketUserRecord.setTicketCategoryId(orderTicketUserCreateDto.getTicketCategoryId());
        orderTicketUserRecord.setOrderPrice(orderTicketUserCreateDto.getOrderPrice());
        return orderTicketUserRecord;
    }

    @Transactional(rollbackFor = Exception.class)
    public String doCreate(OrderCreateDomain orderCreateDomain) {
        LambdaQueryWrapper<Order> orderLambdaQueryWrapper =
                Wrappers.lambdaQuery(Order.class).select(Order::getId).eq(Order::getOrderNumber, orderCreateDomain.getOrderNumber());
        //濡傛灉璁㈠崟瀛樺湪浜嗭紝閭ｄ箞鐩存帴鎷掔粷
        Order oldOrder = orderMapper.selectOne(orderLambdaQueryWrapper);
        if (Objects.nonNull(oldOrder)) {
            return String.valueOf(orderCreateDomain.getOrderNumber());
        }
        Order order = buildOrder(orderCreateDomain);
        order.setId(uidGenerator.getUid());
        order.setDistributionMode("电子票");
        order.setTakeTicketMode("请使用购票人身份证直接入场");
        List<OrderTicketUserCreateDto> orderTicketUserCreateDtoList = orderCreateDomain.getOrderTicketUserCreateDtoList();
        if (CollectionUtil.isEmpty(orderTicketUserCreateDtoList)) {
            throw new TikectsystemFrameException(BaseCode.TICKET_USER_COUNT_UNEQUAL_SEAT_COUNT);
        }
        Date entityCreateTime = DateUtils.now();
        //璐エ浜鸿鍗曞璞?
        List<OrderTicketUser> orderTicketUserList = new ArrayList<>(orderTicketUserCreateDtoList.size());
        //璐エ浜鸿鍗曡褰曞璞?
        List<OrderTicketUserRecord> orderTicketUserRecordList = new ArrayList<>(orderTicketUserCreateDtoList.size());
        for (OrderTicketUserCreateDto orderTicketUserCreateDto : orderTicketUserCreateDtoList) {
            OrderTicketUser orderTicketUser = buildOrderTicketUser(orderTicketUserCreateDto);
            orderTicketUser.setId(uidGenerator.getUid());
            orderTicketUser.setCreateTime(entityCreateTime);
            orderTicketUser.setEditTime(entityCreateTime);
            orderTicketUserList.add(orderTicketUser);

            OrderTicketUserRecord orderTicketUserRecord = buildOrderTicketUserRecord(orderTicketUserCreateDto);
            orderTicketUserRecord.setId(uidGenerator.getUid());
            orderTicketUserRecord.setIdentifierId(orderCreateDomain.getIdentifierId());
            orderTicketUserRecord.setTicketUserOrderId(orderTicketUser.getId());
            orderTicketUserRecord.setRecordTypeCode(RecordType.REDUCE.getCode());
            orderTicketUserRecord.setRecordTypeValue(RecordType.REDUCE.getValue());
            orderTicketUserRecord.setCreateTime(entityCreateTime);
            orderTicketUserRecord.setEditTime(entityCreateTime);
            orderTicketUserRecordList.add(orderTicketUserRecord);
        }
        //鎻掑叆涓昏鍗?
        orderMapper.insert(order);
        //鎻掑叆璐エ浜鸿鍗?
        int insertOrderTicketUserCount = orderTicketUserMapper.batchInsert(orderTicketUserList);
        //鎻掑叆璐エ浜鸿鍗曡褰?
        int insertOrderTicketUserRecordCount = orderTicketUserRecordMapper.batchInsert(orderTicketUserRecordList);
        if (insertOrderTicketUserCount < orderTicketUserList.size() ||
                insertOrderTicketUserRecordCount < orderTicketUserRecordList.size()) {
            throw new TikectsystemFrameException(BaseCode.SYSTEM_ERROR);
        }
        //鎻掑叆璁㈠崟鑺傜洰
        OrderProgram orderProgram = new OrderProgram();
        orderProgram.setId(uidGenerator.getUid());
        orderProgram.setProgramId(order.getProgramId());
        orderProgram.setOrderNumber(order.getOrderNumber());
        orderProgram.setIdentifierId(order.getIdentifierId());
        orderProgramMapper.insert(orderProgram);
        //鐢ㄦ埛涓嬫鑺傜洰鐨勮鍗曟暟閲忓姞1鎿嶄綔
        redisCache.incrBy(RedisKeyBuild.createRedisKey(
                RedisKeyManage.ACCOUNT_ORDER_COUNT,orderCreateDomain.getUserId(),
                orderCreateDomain.getProgramId()),orderTicketUserCreateDtoList.size());
        return String.valueOf(order.getOrderNumber());
    }

    /**
     * 璁㈠崟鍙栨秷锛屼互璁㈠崟缂栧彿鍔犻攣
     * */
    @RepeatExecuteLimit(name = CANCEL_PROGRAM_ORDER,keys = {"#orderCancelDto.orderNumber"})
    @ServiceLock(name = UPDATE_ORDER_STATUS_LOCK,keys = {"#orderCancelDto.orderNumber"})
    @Transactional(rollbackFor = Exception.class)
    public boolean cancel(OrderCancelDto orderCancelDto){
        updateOrderRelatedData(orderCancelDto.getOrderNumber(),OrderStatus.CANCEL);
        return true;
    }

    @ServiceLock(name = UPDATE_ORDER_STATUS_LOCK,keys = {"#orderPayDto.orderNumber"})
    public String pay(OrderPayDto orderPayDto) {
        Long orderNumber = orderPayDto.getOrderNumber();
        LambdaQueryWrapper<Order> orderLambdaQueryWrapper =
                Wrappers.lambdaQuery(Order.class).eq(Order::getOrderNumber, orderNumber);
        Order order = orderMapper.selectOne(orderLambdaQueryWrapper);
        if (Objects.isNull(order)) {
            throw new TikectsystemFrameException(BaseCode.ORDER_NOT_EXIST);
        }
        checkOrderOwner(order, currentLoginUserId());
        if (Objects.equals(order.getOrderStatus(), OrderStatus.CANCEL.getCode())) {
            throw new TikectsystemFrameException(BaseCode.ORDER_CANCEL);
        }
        if (Objects.equals(order.getOrderStatus(), OrderStatus.PAY.getCode())) {
            return SIMPLE_PAY_RESULT;
        }
        if (Objects.equals(order.getOrderStatus(), OrderStatus.REFUND.getCode())) {
            throw new TikectsystemFrameException(BaseCode.ORDER_REFUND);
        }
        if (orderPayDto.getPrice().compareTo(order.getOrderPrice()) != 0) {
            throw new TikectsystemFrameException(BaseCode.PAY_PRICE_NOT_EQUAL_ORDER_PRICE);
        }
        PayDto payDto = getPayDto(orderPayDto, order);
        ApiResponse<String> payResponse = payClient.commonPay(payDto);
        if (payResponse == null) {
            throw new TikectsystemFrameException(BaseCode.PAY_ERROR);
        }
        if (!Objects.equals(payResponse.getCode(), BaseCode.SUCCESS.getCode())) {
            throw new TikectsystemFrameException(payResponse);
        }
        String payResult = Optional.ofNullable(payResponse.getData())
                .orElseThrow(() -> new TikectsystemFrameException(BaseCode.PAY_ERROR));
        try {
            orderService.updateOrderRelatedData(orderNumber,OrderStatus.PAY);
        } catch (TikectsystemFrameException e) {
            if (!Objects.equals(e.getCode(), BaseCode.ORDER_PAY.getCode())) {
                throw e;
            }
        }
        return payResult;
    }

    private PayDto getPayDto(OrderPayDto orderPayDto, Order order) {
        PayDto payDto = new PayDto();
        payDto.setOrderNumber(String.valueOf(order.getOrderNumber()));
        payDto.setPayBillType(orderPayDto.getPayBillType());
        payDto.setSubject(order.getProgramTitle());
        payDto.setChannel("simple");
        payDto.setPlatform(orderPayDto.getPlatform());
        payDto.setPrice(order.getOrderPrice());
        payDto.setNotifyUrl(orderProperties.getOrderPayNotifyUrl());
        payDto.setReturnUrl(orderProperties.getOrderPayReturnUrl());
        return payDto;
    }

    /**
     * 鏀粯鍚庤鍗曟鏌ワ紝浠ヨ鍗曠紪鍙峰姞閿侊紝闃叉澶氭鏇存柊
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
        checkOrderOwner(order, currentLoginUserId());
        BeanUtil.copyProperties(order,orderPayCheckVo);
        if (Objects.equals(order.getOrderStatus(), OrderStatus.PAY.getCode())) {
            return orderPayCheckVo;
        }

        //濡傛灉璁㈠崟宸插彇娑堝垯杩涜閫€娆?
        if (Objects.equals(order.getOrderStatus(), OrderStatus.CANCEL.getCode())) {
            RefundDto refundDto = new RefundDto();
            refundDto.setOrderNumber(String.valueOf(order.getOrderNumber()));
            refundDto.setAmount(order.getOrderPrice());
            refundDto.setChannel("simple");
            refundDto.setReason("寤惰繜璁㈠崟鍏抽棴");
            ApiResponse<String> response = payClient.refund(refundDto);
            if (response != null && Objects.equals(response.getCode(), BaseCode.SUCCESS.getCode())) {
                //璋冪敤鏀粯鏈嶅姟閫€娆炬垚鍔熷悗锛屾妸璁㈠崟鏇存柊涓哄凡閫€娆剧姸鎬?
                Order updateOrder = new Order();
                updateOrder.setEditTime(DateUtils.now());
                updateOrder.setOrderStatus(OrderStatus.REFUND.getCode());
                orderMapper.update(updateOrder,Wrappers.lambdaUpdate(Order.class).eq(Order::getOrderNumber, order.getOrderNumber()));
                orderPayCheckVo.setOrderStatus(OrderStatus.REFUND.getCode());
                orderPayCheckVo.setCancelOrderTime(DateUtils.now());
            }else {
                log.error("pay鏈嶅姟閫€娆惧け璐?dto : {} response : {}",JSON.toJSONString(refundDto),JSON.toJSONString(response));
            }
            return orderPayCheckVo;
        }

        //璋冪敤鏀粯鏈嶅姟鏌ヨ鏈湴鏀粯璐﹀崟鐘舵€?
        TradeCheckDto tradeCheckDto = new TradeCheckDto();
        tradeCheckDto.setOutTradeNo(String.valueOf(orderPayCheckDto.getOrderNumber()));
        tradeCheckDto.setChannel("simple");
        ApiResponse<TradeCheckVo> tradeCheckVoApiResponse = payClient.tradeCheck(tradeCheckDto);
        if (tradeCheckVoApiResponse == null) {
            throw new TikectsystemFrameException(BaseCode.PAY_TRADE_CHECK_ERROR);
        }
        if (!Objects.equals(tradeCheckVoApiResponse.getCode(), BaseCode.SUCCESS.getCode())) {
            throw new TikectsystemFrameException(tradeCheckVoApiResponse);
        }
        TradeCheckVo tradeCheckVo = Optional.ofNullable(tradeCheckVoApiResponse.getData())
                .orElseThrow(() -> new TikectsystemFrameException(BaseCode.PAY_BILL_NOT_EXIST));
        if (tradeCheckVo.isSuccess()) {
            Integer payBillStatus = tradeCheckVo.getPayBillStatus();
            Integer orderStatus = order.getOrderStatus();
            if (Objects.equals(payBillStatus, PayBillStatus.PAY.getCode()) &&
                    (tradeCheckVo.getTotalAmount() == null ||
                            tradeCheckVo.getTotalAmount().compareTo(order.getOrderPrice()) != 0)) {
                throw new TikectsystemFrameException(BaseCode.PAY_PRICE_NOT_EQUAL_ORDER_PRICE);
            }
            //濡傛灉璁㈠崟鐨勭姸鎬佸拰璐﹀崟鐨勭姸鎬佷笉涓€鑷达紝鍒欏湪鏈妫€鏌ヤ腑琛ュ伩鏇存柊
            if (!Objects.equals(orderStatus, payBillStatus)) {
                orderPayCheckVo.setOrderStatus(payBillStatus);
                try {
                    //濡傛灉璐﹀崟鐨勭姸鎬佹槸鏀粯锛岄偅涔堟墽琛岃鍗曟敮浠樼殑鎿嶄綔
                    if (Objects.equals(payBillStatus, PayBillStatus.PAY.getCode())) {
                        orderPayCheckVo.setPayOrderTime(DateUtils.now());
                        orderService.updateOrderRelatedData(order.getOrderNumber(),OrderStatus.PAY);
                        //濡傛灉璐﹀崟鐨勭姸鎬佹槸鍙栨秷锛岄偅涔堟墽琛岃鍗曞彇娑堢殑鎿嶄綔
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
        //灏嗗洖璋冧腑鐨勫弬鏁拌浆涓篗ap缁撴瀯
        Map<String, String> params = new HashMap<>(256);
        if (request instanceof CustomizeRequestWrapper) {
            CustomizeRequestWrapper customizeRequestWrapper = (CustomizeRequestWrapper)request;
            String requestBody = customizeRequestWrapper.getRequestBody();
            params = StringUtil.convertQueryStringToMap(requestBody);
        }
        //鑾峰彇鍏朵腑鐨勮鍗曞彿
        String outTradeNo = params.get("out_trade_no");
        if (StringUtil.isEmpty(outTradeNo)) {
            return "failure";
        }
        //鍔犻攣锛岄槻姝㈠苟鍙戦棶棰?
        RLock lock = serviceLockTool.getLock(LockType.Reentrant, UPDATE_ORDER_STATUS_LOCK,
                new String[]{outTradeNo});
        lock.lock();
        try {
            Order order = orderMapper.selectOne(Wrappers.lambdaQuery(Order.class).eq(Order::getOrderNumber, outTradeNo));
            if (Objects.isNull(order)) {
                throw new TikectsystemFrameException(BaseCode.ORDER_NOT_EXIST);
            }
            //濡傛灉璁㈠崟宸插彇娑堝垯杩涜閫€娆?
            if (Objects.equals(order.getOrderStatus(), OrderStatus.CANCEL.getCode())) {
                RefundDto refundDto = new RefundDto();
                refundDto.setOrderNumber(outTradeNo);
                refundDto.setAmount(order.getOrderPrice());
                refundDto.setChannel("simple");
                refundDto.setReason("寤惰繜璁㈠崟鍏抽棴");
                ApiResponse<String> response = payClient.refund(refundDto);
                if (response != null && Objects.equals(response.getCode(), BaseCode.SUCCESS.getCode())) {
                    //璋冪敤鏀粯鏈嶅姟閫€娆炬垚鍔熷悗锛屾妸璁㈠崟鏇存柊涓哄凡閫€娆剧姸鎬?
                    Order updateOrder = new Order();
                    updateOrder.setEditTime(DateUtils.now());
                    updateOrder.setOrderStatus(OrderStatus.REFUND.getCode());
                    orderMapper.update(updateOrder,Wrappers.lambdaUpdate(Order.class).eq(Order::getOrderNumber, outTradeNo));
                }else {
                    log.error("pay鏈嶅姟閫€娆惧け璐?dto : {} response : {}",JSON.toJSONString(refundDto),JSON.toJSONString(response));
                }
                if (response == null || !Objects.equals(response.getCode(), BaseCode.SUCCESS.getCode())) {
                    return ALIPAY_NOTIFY_FAILURE_RESULT;
                }
                return ALIPAY_NOTIFY_SUCCESS_RESULT;
            }

            try {
                orderService.updateOrderRelatedData(Long.parseLong(outTradeNo), OrderStatus.PAY);
            }catch (Exception e) {
                log.warn("updateOrderRelatedData warn message",e);
            }
            return ALIPAY_NOTIFY_SUCCESS_RESULT;
        }finally {
            lock.unlock();
        }

    }

    /**
     * 鏇存柊璁㈠崟鍜岃喘绁ㄤ汉璁㈠崟鐘舵€佷互鍙婃搷浣滅紦瀛樻暟鎹?
     * */
    @Transactional(rollbackFor = Exception.class)
    public void updateOrderRelatedData(Long orderNumber,OrderStatus orderStatus){
        //濡傛灉涓嶆槸鍙栨秷鎴栬€呮敮浠樻搷浣滐紝鍒欑洿鎺ユ姏鍑哄紓甯告彁绀?
        if (!(Objects.equals(orderStatus.getCode(), OrderStatus.CANCEL.getCode()) ||
                Objects.equals(orderStatus.getCode(), OrderStatus.PAY.getCode()))) {
            throw new TikectsystemFrameException(  BaseCode.OPERATE_ORDER_STATUS_NOT_PERMIT);
        }
        //鏌ヨ璁㈠崟
        LambdaQueryWrapper<Order> orderLambdaQueryWrapper =
                Wrappers.lambdaQuery(Order.class).eq(Order::getOrderNumber, orderNumber);
        Order order = orderMapper.selectOne(orderLambdaQueryWrapper);
        //妫€鏌ヨ鍗曠殑鐘舵€?宸插彇娑堛€佸凡鏀粯銆佸凡閫€鍗曠殑鐘舵€佷笉鍐嶆墽琛?
        checkOrderStatus(order);
        //鏌ヨ璇ヨ鍗曚笅鐨勮喘绁ㄤ汉璁㈠崟鍒楄〃
        LambdaQueryWrapper<OrderTicketUser> orderTicketUserLambdaQueryWrapper =
                Wrappers.lambdaQuery(OrderTicketUser.class).eq(OrderTicketUser::getOrderNumber, order.getOrderNumber());
        List<OrderTicketUser> orderTicketUserList = orderTicketUserMapper.selectList(orderTicketUserLambdaQueryWrapper);
        if (CollectionUtil.isEmpty(orderTicketUserList)) {
            throw new TikectsystemFrameException(BaseCode.TICKET_USER_ORDER_NOT_EXIST);
        }
        //灏嗚鍗曟洿鏂颁负鍙栨秷鎴栬€呮敮浠樼姸鎬?
        Order updateOrder = new Order();
        updateOrder.setId(order.getId());
        updateOrder.setOrderStatus(orderStatus.getCode());
        //灏嗚喘绁ㄤ汉璁㈠崟鏇存柊涓哄彇娑堟垨鑰呮敮浠樼姸鎬?
        OrderTicketUser updateOrderTicketUser = new OrderTicketUser();
        updateOrderTicketUser.setOrderStatus(orderStatus.getCode());

        //濡傛灉鏄敮浠樼殑璇濓紝閭ｄ箞璁板綍鐨勭被鍨嬪氨鏄敼鍙樼姸鎬?
        //璁板綍绫诲瀷code
        Integer recordTypeCode = RecordType.CHANGE_STATUS.getCode();
        //璁板綍绫诲瀷鍊?
        String recordTypeValue = RecordType.CHANGE_STATUS.getValue();
        //鏀粯鐘舵€佺殑鎿嶄綔
        if (Objects.equals(orderStatus.getCode(), OrderStatus.PAY.getCode())) {
            updateOrder.setPayOrderTime(DateUtils.now());
            updateOrderTicketUser.setPayOrderTime(DateUtils.now());
        } else if (Objects.equals(orderStatus.getCode(), OrderStatus.CANCEL.getCode())) {
            //鍙栨秷鐘舵€佺殑鎿嶄綔
            updateOrder.setCancelOrderTime(DateUtils.now());
            updateOrderTicketUser.setCancelOrderTime(DateUtils.now());
            //濡傛灉鏄彇娑堢殑璇濓紝閭ｄ箞璁板綍鐨勭被鍨嬪氨鏄鍔犱綑绁?
            recordTypeCode = RecordType.INCREASE.getCode();
            recordTypeValue = RecordType.INCREASE.getValue();
        }
        //鏇存柊璁㈠崟
        LambdaUpdateWrapper<Order> orderLambdaUpdateWrapper =
                Wrappers.lambdaUpdate(Order.class).eq(Order::getOrderNumber, order.getOrderNumber());
        int updateOrderResult = orderMapper.update(updateOrder,orderLambdaUpdateWrapper);
        //鏇存柊璐エ浜鸿鍗?
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
            //璐エ浜鸿鍗曡褰?
            OrderTicketUserRecord orderTicketUserRecord = new OrderTicketUserRecord();
            BeanUtils.copyProperties(orderTicketUser,orderTicketUserRecord);
            orderTicketUserRecord.setId(uidGenerator.getUid());
            orderTicketUserRecord.setIdentifierId(order.getIdentifierId());
            orderTicketUserRecord.setTicketUserOrderId(orderTicketUser.getId());
            orderTicketUserRecord.setRecordTypeCode(recordTypeCode);
            orderTicketUserRecord.setRecordTypeValue(recordTypeValue);
            orderTicketUserRecordList.add(orderTicketUserRecord);
            //璐エ浜鸿鍗昳d鍜屽骇浣峣d
            seatIdAndTicketUserIdDomainList.add(new SeatIdAndTicketUserIdDomain(orderTicketUser.getSeatId(),
                    orderTicketUser.getTicketUserId()));
        }
        //娣诲姞璐エ浜鸿鍗曡褰曟祦姘?
        orderTicketUserRecordService.saveBatch(orderTicketUserRecordList);

        //濡傛灉鏄彇娑堟搷浣滐紝閭ｄ箞鎶婄敤鎴蜂笅璇ヨ妭鐩殑璁㈠崟鏁伴噺瑕?1
        if (Objects.equals(orderStatus.getCode(), OrderStatus.CANCEL.getCode())) {
            redisCache.incrBy(RedisKeyBuild.createRedisKey(
                    RedisKeyManage.ACCOUNT_ORDER_COUNT,order.getUserId(),order.getProgramId()),-updateTicketUserOrderResult);
        }
        Long programId = order.getProgramId();
        //灏嗚喘绁ㄤ汉璁㈠崟闆嗗悎杞崲鎴恗ap缁撴瀯锛宬ey锛氱エ妗d value锛氳喘绁ㄤ汉璁㈠崟
        Map<Long, List<OrderTicketUser>> orderTicketUserSeatList =
                orderTicketUserList.stream().collect(Collectors.groupingBy(OrderTicketUser::getTicketCategoryId));
        Map<Long,List<Long>> seatMap = new HashMap<>(orderTicketUserSeatList.size());
        //鏍规嵁orderTicketUserSeatList寰楀埌seatMap
        //seatMap缁撴瀯 key锛氱エ妗d  value锛氬骇浣峣d闆嗗悎
        orderTicketUserSeatList.forEach((k,v) -> {
            seatMap.put(k,v.stream().map(OrderTicketUser::getSeatId).collect(Collectors.toList()));
        });
        //鏇存柊缂撳瓨鍜岃妭鐩簱鐩稿叧鏁版嵁
        try {
            updateProgramRelatedDataResolution(programId,seatMap,orderStatus,order.getIdentifierId(),order.getUserId(),
                    seatIdAndTicketUserIdDomainList,order.getOrderVersion());
        } catch (Exception e) {
            if (Objects.equals(orderStatus.getCode(), OrderStatus.PAY.getCode())) {
                log.warn("order paid, but program related data update failed, orderNumber:{}", orderNumber, e);
            } else {
                throw e;
            }
        }
        if (Objects.equals(order.getOrderVersion(), ProgramOrderVersion.V4_VERSION.getValue()) &&
                Objects.equals(orderStatus.getCode(), OrderStatus.CANCEL.getCode())) {
            markOrderRequestCancelledSafely(orderNumber);
        }
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
        if (seatMap == null || seatMap.isEmpty()) {
            throw new TikectsystemFrameException(BaseCode.SEAT_ID_EMPTY);
        }
        List<Long> operateSeatIdList = new ArrayList<>();
        List<TicketCategoryCountDto> ticketCategoryCountDtoList = new ArrayList<>(seatMap.size());
        seatMap.forEach((ticketCategoryId, seatIdList) -> {
            if (CollectionUtil.isEmpty(seatIdList)) {
                return;
            }
            operateSeatIdList.addAll(seatIdList);
            ticketCategoryCountDtoList.add(new TicketCategoryCountDto(ticketCategoryId, (long) seatIdList.size()));
        });
        if (CollectionUtil.isEmpty(operateSeatIdList)) {
            throw new TikectsystemFrameException(BaseCode.SEAT_ID_EMPTY);
        }
        Map<Long, List<SeatVo>> seatVoMap = new HashMap<>(seatMap.size());
        seatMap.forEach((k,v) -> {
            if (CollectionUtil.isEmpty(v)) {
                return;
            }
            seatVoMap.put(k,redisCache.multiGetForHash(
                    RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_SEAT_LOCK_RESOLUTION_HASH, programId, k),
                    v.stream().map(String::valueOf).collect(Collectors.toList()), SeatVo.class));
        });
        if (CollectionUtil.isEmpty(seatVoMap)) {
            throw new TikectsystemFrameException(BaseCode.LOCK_SEAT_LIST_EMPTY);
        }
        JSONArray jsonArray = new JSONArray();
        for (TicketCategoryCountDto ticketCategoryCountDto : ticketCategoryCountDtoList) {
            JSONObject ticketCategoryJsonObject = new JSONObject();
            ticketCategoryJsonObject.put("programTicketRemainNumberHashKey",RedisKeyBuild.createRedisKey(
                    RedisKeyManage.PROGRAM_TICKET_REMAIN_NUMBER_HASH_RESOLUTION,
                    programId, ticketCategoryCountDto.getTicketCategoryId()).getRelKey());
            ticketCategoryJsonObject.put("ticketCategoryId",String.valueOf(ticketCategoryCountDto.getTicketCategoryId()));
            ticketCategoryJsonObject.put("count",ticketCategoryCountDto.getCount());
            jsonArray.add(ticketCategoryJsonObject);
        }
        JSONArray addSeatDatajsonArray = new JSONArray();
        JSONArray unLockSeatIdjsonArray = new JSONArray();
        seatVoMap.forEach((k,v) -> {
            if (CollectionUtil.isEmpty(v)) {
                log.warn("program seat lock cache is empty, programId:{}, ticketCategoryId:{}, seatIds:{}",
                        programId, k, seatMap.get(k));
                return;
            }
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
                    //搴т綅鐘舵€佽鏀规垚鏈敭鍗?
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
        programOperateDataDto.setSeatIdList(operateSeatIdList);
        programOperateDataDto.setTicketCategoryCountDtoList(ticketCategoryCountDtoList);
        programOperateDataDto.setOrderVersion(orderVersion);
        //濡傛灉鍒涘缓璁㈠崟鐗堟湰鏄痸1锛寁2锛寁3
        if (!Objects.equals(orderVersion, ProgramOrderVersion.V4_VERSION.getValue())){
            orderProgramCacheResolutionOperate.programCacheReverseOperate(keys,data);
            if (Objects.equals(orderStatus.getCode(), OrderStatus.PAY.getCode())) {
                programOperateDataDto.setSellStatus(SellStatus.SOLD.getCode());
                delayOperateProgramDataSend.sendMessage(JSON.toJSONString(programOperateDataDto));
            }
        }else {
            //濡傛灉鍒涘缓璁㈠崟鐗堟湰鏄痸4 鏇存柊鑺傜洰鏈嶅姟鐨勭浉鍏虫暟鎹?
            if (Objects.equals(orderStatus.getCode(), OrderStatus.PAY.getCode()) ||
                    Objects.equals(orderStatus.getCode(), OrderStatus.CANCEL.getCode())) {
                boolean payStatus = Objects.equals(orderStatus.getCode(), OrderStatus.PAY.getCode());
                programOperateDataDto.setSellStatus(payStatus ? SellStatus.SOLD.getCode() : SellStatus.NO_SOLD.getCode());
                try {
                    ApiResponse<Boolean> programApiResponse = programClient.operateProgramData(programOperateDataDto);
                    if (programApiResponse == null || !Objects.equals(programApiResponse.getCode(), BaseCode.SUCCESS.getCode())) {
                        throw new TikectsystemFrameException(programApiResponse);
                    }
                } catch (RuntimeException e) {
                    if (!payStatus) {
                        throw e;
                    }
                    delayOperateProgramDataSend.sendMessage(JSON.toJSONString(programOperateDataDto));
                    log.warn("order paid, program db update failed and retry message sent, programId:{}, seatIds:{}",
                            programId, operateSeatIdList, e);
                }
            }
            orderProgramCacheResolutionOperate.programCacheReverseOperate(keys,data);
        }
    }

    public List<OrderListVo> selectList(OrderListDto orderListDto) {
        Long currentUserId = currentLoginUserId();
        List<OrderListVo> orderListVos = new ArrayList<>();
        LambdaQueryWrapper<Order> orderLambdaQueryWrapper =
                Wrappers.lambdaQuery(Order.class).eq(Order::getUserId, currentUserId);
        //鏌ヨ涓昏鍗曞垪琛?
        List<Order> orderList = orderMapper.selectList(orderLambdaQueryWrapper);
        if (CollectionUtil.isEmpty(orderList)) {
            return orderListVos;
        }
        orderListVos = BeanUtil.copyToList(orderList, OrderListVo.class);
        //姣忎釜璁㈠崟涓嬬殑璐エ浜鸿鍗曟暟閲忕粺璁?
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
        //鏌ヨ璁㈠崟
        LambdaQueryWrapper<Order> orderLambdaQueryWrapper =
                Wrappers.lambdaQuery(Order.class).eq(Order::getOrderNumber, orderGetDto.getOrderNumber());
        Order order = orderMapper.selectOne(orderLambdaQueryWrapper);
        if (Objects.isNull(order)) {
            throw new TikectsystemFrameException(BaseCode.ORDER_NOT_EXIST);
        }
        checkOrderOwner(order, currentLoginUserId());
        //鏌ヨ璐エ浜鸿鍗?
        LambdaQueryWrapper<OrderTicketUser> orderTicketUserLambdaQueryWrapper =
                Wrappers.lambdaQuery(OrderTicketUser.class).eq(OrderTicketUser::getOrderNumber, order.getOrderNumber());
        List<OrderTicketUser> orderTicketUserList = orderTicketUserMapper.selectList(orderTicketUserLambdaQueryWrapper);
        if (CollectionUtil.isEmpty(orderTicketUserList)) {
            throw new TikectsystemFrameException(BaseCode.TICKET_USER_ORDER_NOT_EXIST);
        }

        OrderGetVo orderGetVo = new OrderGetVo();
        BeanUtil.copyProperties(order,orderGetVo);

        //缁勮璐エ璁㈠崟淇℃伅
        List<OrderTicketInfoVo> orderTicketInfoVoList = new ArrayList<>();
        //鎸夌収璐エ璁㈠崟鐨勯噾棰濊繘琛屽垎缁?
        Map<BigDecimal, List<OrderTicketUser>> orderTicketUserMap =
                orderTicketUserList.stream().collect(Collectors.groupingBy(OrderTicketUser::getOrderPrice));
        orderTicketUserMap.forEach((k,v) -> {
            OrderTicketInfoVo orderTicketInfoVo = new OrderTicketInfoVo();
            String seatInfo = "鏆傛棤搴т綅淇℃伅";
            //濡傛灉鑺傜洰鏄厑璁搁€夊骇鐨勶紝鎵嶆樉绀哄嚭褰撴椂鐢熸垚璁㈠崟鏃朵骇鐢熺殑搴т綅淇℃伅
            if (Objects.equals(order.getProgramPermitChooseSeat(),BusinessStatus.YES.getCode())) {
                seatInfo = v.stream().map(OrderTicketUser::getSeatInfo).collect(Collectors.joining(","));
            }
            orderTicketInfoVo.setSeatInfo(seatInfo);
            orderTicketInfoVo.setPrice(k);
            orderTicketInfoVo.setQuantity(v.size());
            orderTicketInfoVo.setRelPrice(v.stream().map(OrderTicketUser::getOrderPrice)
                    .reduce(BigDecimal.ZERO,BigDecimal::add));
            orderTicketInfoVoList.add(orderTicketInfoVo);
        });

        orderGetVo.setOrderTicketInfoVoList(orderTicketInfoVoList);

        //鏌ヨ鐢ㄦ埛鍜岃喘绁ㄤ汉淇℃伅
        UserGetAndTicketUserListDto userGetAndTicketUserListDto = new UserGetAndTicketUserListDto();
        userGetAndTicketUserListDto.setUserId(order.getUserId());
        ApiResponse<UserGetAndTicketUserListVo> userGetAndTicketUserApiResponse =
                userClient.getUserAndTicketUserList(userGetAndTicketUserListDto);

        if (userGetAndTicketUserApiResponse == null || !Objects.equals(userGetAndTicketUserApiResponse.getCode(), BaseCode.SUCCESS.getCode())) {
            throw new TikectsystemFrameException(userGetAndTicketUserApiResponse);

        }
        //楠岃瘉鐢ㄦ埛鍜岃喘绁ㄤ汉淇℃伅鏄惁瀛樺湪
        UserGetAndTicketUserListVo userAndTicketUserListVo =
                Optional.ofNullable(userGetAndTicketUserApiResponse.getData())
                        .orElseThrow(() -> new TikectsystemFrameException(BaseCode.RPC_RESULT_DATA_EMPTY));
        //濡傛灉鐢ㄦ埛淇℃伅绌猴紝鎶涘嚭寮傚父
        if (Objects.isNull(userAndTicketUserListVo.getUserVo())) {
            throw new TikectsystemFrameException(BaseCode.USER_EMPTY);
        }
        //濡傛灉璐エ浜轰俊鎭┖锛屾姏鍑哄紓甯?
        if (CollectionUtil.isEmpty(userAndTicketUserListVo.getTicketUserVoList())) {
            throw new TikectsystemFrameException(BaseCode.TICKET_USER_EMPTY);
        }
        //浠庢煡璇㈠緱鍒扮殑璐エ浜轰俊鎭腑杩涜杩囨护鍑鸿璁㈠崟涓嬭喘绁ㄤ汉鐨勪俊鎭?
        List<TicketUserVo> ticketUserVoList = userAndTicketUserListVo.getTicketUserVoList();
        List<TicketUserVo> filterTicketUserVoList = new ArrayList<>(orderTicketUserList.size());
        Map<Long, TicketUserVo> ticketUserVoMap = ticketUserVoList
                .stream().collect(Collectors.toMap(TicketUserVo::getId, ticketUserVo -> ticketUserVo, (v1, v2) -> v2));
        for (OrderTicketUser orderTicketUser : orderTicketUserList) {
            TicketUserVo ticketUserVo = resolveOrderTicketUser(orderTicketUser, ticketUserVoList, ticketUserVoMap);
            if (Objects.isNull(ticketUserVo)) {
                log.warn("order ticket user missing, orderNumber:{}, userId:{}, ticketUserId:{}",
                        order.getOrderNumber(), order.getUserId(), orderTicketUser.getTicketUserId());
                continue;
            }
            filterTicketUserVoList.add(ticketUserVo);
        }
        //缁勮鏁版嵁
        UserInfoVo userInfoVo = new UserInfoVo();
        BeanUtil.copyProperties(userAndTicketUserListVo.getUserVo(),userInfoVo);
        UserAndTicketUserInfoVo userAndTicketUserInfoVo = new UserAndTicketUserInfoVo();
        userAndTicketUserInfoVo.setUserInfoVo(userInfoVo);
        userAndTicketUserInfoVo.setTicketUserInfoVoList(BeanUtil.copyToList(filterTicketUserVoList, TicketUserInfoVo.class));
        orderGetVo.setUserAndTicketUserInfoVo(userAndTicketUserInfoVo);

        return orderGetVo;
    }

    /**
     * 查询订单主表状态。
     * 内部服务只需要判断订单事实和可支付状态时使用，避免订单详情组装依赖购票人明细与用户 RPC。
     *
     * @param orderGetDto 订单查询参数
     * @return 仅填充订单主表关键字段的订单视图
     */
    public OrderGetVo getStatus(OrderGetDto orderGetDto) {
        Order order = orderMapper.selectOne(Wrappers.lambdaQuery(Order.class)
                .select(Order::getOrderNumber, Order::getOrderStatus, Order::getProgramId, Order::getUserId)
                .eq(Order::getOrderNumber, orderGetDto.getOrderNumber()));
        if (Objects.isNull(order)) {
            throw new TikectsystemFrameException(BaseCode.ORDER_NOT_EXIST);
        }
        OrderGetVo orderGetVo = new OrderGetVo();
        orderGetVo.setOrderNumber(order.getOrderNumber());
        orderGetVo.setOrderStatus(order.getOrderStatus());
        orderGetVo.setProgramId(order.getProgramId());
        orderGetVo.setUserId(order.getUserId());
        return orderGetVo;
    }

    private TicketUserVo resolveOrderTicketUser(OrderTicketUser orderTicketUser,
                                                List<TicketUserVo> ticketUserVoList,
                                                Map<Long, TicketUserVo> ticketUserVoMap) {
        Long ticketUserId = orderTicketUser.getTicketUserId();
        TicketUserVo exactTicketUserVo = ticketUserVoMap.get(ticketUserId);
        if (Objects.nonNull(exactTicketUserVo)) {
            return exactTicketUserVo;
        }
        TicketUserVo closestTicketUserVo = null;
        long closestDiff = Long.MAX_VALUE;
        boolean ambiguousClosest = false;
        for (TicketUserVo ticketUserVo : ticketUserVoList) {
            if (Objects.isNull(ticketUserVo.getId()) || Objects.isNull(ticketUserId)) {
                continue;
            }
            long diff = Math.abs(ticketUserVo.getId() - ticketUserId);
            if (diff < closestDiff) {
                closestDiff = diff;
                closestTicketUserVo = ticketUserVo;
                ambiguousClosest = false;
            } else if (diff == closestDiff) {
                ambiguousClosest = true;
            }
        }
        if (!ambiguousClosest && Objects.nonNull(closestTicketUserVo) &&
                closestDiff <= TICKET_USER_ID_ROUNDING_TOLERANCE) {
            log.warn("order ticket user id rounded, orderNumber:{}, sourceTicketUserId:{}, matchedTicketUserId:{}",
                    orderTicketUser.getOrderNumber(), ticketUserId, closestTicketUserVo.getId());
            return closestTicketUserVo;
        }
        if (ticketUserVoList.size() == 1) {
            TicketUserVo onlyTicketUserVo = ticketUserVoList.get(0);
            log.warn("order ticket user fallback to only user ticket, orderNumber:{}, sourceTicketUserId:{}, matchedTicketUserId:{}",
                    orderTicketUser.getOrderNumber(), ticketUserId, onlyTicketUserVo.getId());
            return onlyTicketUserVo;
        }
        return null;
    }

    /**
     * 查询当前登录用户在指定节目下的订单数量。
     * 该接口服务于节目下单链路，用户身份以网关注入并经 Feign 透传的上下文为准。
     *
     * @param accountOrderCountDto 节目维度查询参数
     * @return 当前登录用户在节目下的订单数量
     */
    public AccountOrderCountVo accountOrderCount(AccountOrderCountDto accountOrderCountDto) {
        Long currentUserId = currentLoginUserId();
        if (Objects.nonNull(accountOrderCountDto.getUserId()) &&
                !Objects.equals(accountOrderCountDto.getUserId(), currentUserId)) {
            throw new TikectsystemFrameException(BaseCode.USER_EMPTY);
        }
        AccountOrderCountVo accountOrderCountVo = new AccountOrderCountVo();
        accountOrderCountVo.setCount(orderMapper.accountOrderCount(currentUserId,
                accountOrderCountDto.getProgramId()));
        return accountOrderCountVo;
    }


    @RepeatExecuteLimit(name = CREATE_PROGRAM_ORDER_MQ,keys = {"#orderCreateMq.orderNumber"})
    @Transactional(rollbackFor = Exception.class)
    public String createMq(OrderCreateMq orderCreateMq){
        if (!Objects.equals(orderCreateMq.getOrderVersion(), ProgramOrderVersion.V4_VERSION.getValue())) {
            List<OrderTicketUserCreateDto> orderTicketUserCreateDtoList = orderCreateMq.getOrderTicketUserCreateDtoList();
            List<Long> seatIdList = new ArrayList<>(orderTicketUserCreateDtoList.size());
            Map<Long, Long> countMap = new HashMap<>(orderTicketUserCreateDtoList.size());
            for (OrderTicketUserCreateDto orderTicketUserCreateDto : orderTicketUserCreateDtoList) {
                seatIdList.add(orderTicketUserCreateDto.getSeatId());
                countMap.merge(orderTicketUserCreateDto.getTicketCategoryId(), 1L, Long::sum);
            }
            List<TicketCategoryCountDto> ticketCountList = new ArrayList<>(countMap.size());
            countMap.forEach((ticketCategoryId, ticketCount) ->
                    ticketCountList.add(new TicketCategoryCountDto(ticketCategoryId, ticketCount)));
            //淇敼鑺傜洰鏈嶅姟涓殑搴т綅鐘舵€佸拰鎵ｅ噺搴撳瓨
            ReduceRemainNumberDto reduceRemainNumberDto = new ReduceRemainNumberDto();
            reduceRemainNumberDto.setProgramId(orderCreateMq.getProgramId());
            reduceRemainNumberDto.setSellStatus(SellStatus.LOCK.getCode());
            reduceRemainNumberDto.setSeatIdList(seatIdList);
            reduceRemainNumberDto.setTicketCategoryCountDtoList(ticketCountList);
            ApiResponse<Boolean> programApiResponse = programClient.operateSeatLockAndTicketCategoryRemainNumber(reduceRemainNumberDto);
            if (programApiResponse == null || !Objects.equals(programApiResponse.getCode(), BaseCode.SUCCESS.getCode())) {
                //灏嗗洜涓轰慨鏀硅妭鐩湇鍔′綑绁ㄥ拰搴т綅澶辫触锛屽鑷翠涪寮冪殑璁㈠崟鏀惧叆redis涓?
                redisCache.leftPushForList(RedisKeyBuild.createRedisKey(RedisKeyManage.DISCARD_ORDER,
                        orderCreateMq.getProgramId()),new DiscardOrder(orderCreateMq, DiscardOrderReason.MODIFY_PROGRAM_REMAIN_NUMBER_SEAT_FAIL.getCode()));
                throw new TikectsystemFrameException(programApiResponse);
            }
        }
        // 真正地创建订单
        String orderNumber = createByMq(orderCreateMq);
        afterCurrentTransactionCommit(() -> cacheCreatedOrderNumberSafely(orderNumber));
        if (Objects.equals(orderCreateMq.getOrderVersion(), ProgramOrderVersion.V4_VERSION.getValue())) {
            afterCurrentTransactionCommit(() -> {
                sendDelayOrderCancel(orderCreateMq);
                markOrderRequestCreatedSafely(orderCreateMq);
            });
        }
        return orderNumber;
    }

    private void afterCurrentTransactionCommit(Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }

    private void cacheCreatedOrderNumberSafely(String orderNumber) {
        try {
            redisCache.set(RedisKeyBuild.createRedisKey(RedisKeyManage.ORDER_MQ, orderNumber),
                    orderNumber, 1, TimeUnit.MINUTES);
        } catch (RuntimeException e) {
            log.warn("cache created order number failed, orderNumber:{}", orderNumber, e);
        }
    }

    private void markOrderRequestCreatedSafely(OrderCreateMq orderCreateMq) {
        if (orderCreateMq == null || orderCreateMq.getOrderNumber() == null) {
            return;
        }
        try {
            if (updateOrderRequestResult(orderCreateMq.getOrderNumber(), ORDER_REQUEST_RESULT_RESERVED,
                    ORDER_REQUEST_RESULT_ORDER_CREATED)) {
                return;
            }
            if (updateOrderRequestResult(orderCreateMq.getOrderNumber(), ORDER_REQUEST_RESULT_PROCESSING,
                    ORDER_REQUEST_RESULT_ORDER_CREATED)) {
                return;
            }
            if (updateOrderRequestResult(orderCreateMq.getOrderNumber(), ORDER_REQUEST_RESULT_FAILED,
                    ORDER_REQUEST_RESULT_ORDER_CREATED)) {
                return;
            }
            if (updateOrderRequestResult(orderCreateMq.getOrderNumber(), ORDER_REQUEST_RESULT_EXPIRED,
                    ORDER_REQUEST_RESULT_ORDER_CREATED)) {
                return;
            }
            updateOrderRequestResult(orderCreateMq.getOrderNumber(), ORDER_REQUEST_RESULT_CANCELLED,
                    ORDER_REQUEST_RESULT_ORDER_CREATED);
        } catch (RuntimeException e) {
            log.warn("mark order request created failed, orderNumber:{}", orderCreateMq.getOrderNumber(), e);
        }
    }

    private void markOrderRequestCancelledSafely(Long orderNumber) {
        if (orderNumber == null) {
            return;
        }
        try {
            if (updateOrderRequestResult(orderNumber, ORDER_REQUEST_RESULT_ORDER_CREATED,
                    ORDER_REQUEST_RESULT_CANCELLED)) {
                return;
            }
            if (updateOrderRequestResult(orderNumber, ORDER_REQUEST_RESULT_RESERVED,
                    ORDER_REQUEST_RESULT_CANCELLED)) {
                return;
            }
            updateOrderRequestResult(orderNumber, ORDER_REQUEST_RESULT_PROCESSING,
                    ORDER_REQUEST_RESULT_CANCELLED);
        } catch (RuntimeException e) {
            log.warn("mark order request cancelled failed, orderNumber:{}", orderNumber, e);
        }
    }

    /**
     * create_order 消息确定不可建单时回写异步下单失败状态。
     *
     * @param orderCreateMq 创建订单消息
     * @param baseCode 失败原因
     */
    public void markCreateOrderRequestFailedSafely(OrderCreateMq orderCreateMq, BaseCode baseCode) {
        if (orderCreateMq == null || orderCreateMq.getOrderNumber() == null || baseCode == null) {
            return;
        }
        try {
            if (updateOrderRequestResult(orderCreateMq.getOrderNumber(), ORDER_REQUEST_RESULT_RESERVED,
                    ORDER_REQUEST_RESULT_FAILED, baseCode)) {
                return;
            }
            updateOrderRequestResult(orderCreateMq.getOrderNumber(), ORDER_REQUEST_RESULT_PROCESSING,
                    ORDER_REQUEST_RESULT_FAILED, baseCode);
        } catch (RuntimeException e) {
            log.warn("mark create order request failed status error, orderNumber:{}", orderCreateMq.getOrderNumber(), e);
        }
    }

    private boolean updateOrderRequestResult(Long orderNumber, String beforeStatus, String status) {
        return updateOrderRequestResult(orderNumber, beforeStatus, status, null);
    }

    private boolean updateOrderRequestResult(Long orderNumber, String beforeStatus, String status, BaseCode failCode) {
        OrderRequestResultUpdateDto updateDto = new OrderRequestResultUpdateDto();
        updateDto.setOrderNumber(orderNumber);
        updateDto.setBeforeStatus(beforeStatus);
        updateDto.setStatus(status);
        if (failCode != null) {
            updateDto.setFailCode(String.valueOf(failCode.getCode()));
            updateDto.setFailMessage(failCode.getMsg());
        }
        ApiResponse<Boolean> response = programClient.updateOrderRequestResult(updateDto);
        if (response == null || !Objects.equals(response.getCode(), BaseCode.SUCCESS.getCode())) {
            log.warn("update order request result response error, orderNumber:{}, beforeStatus:{}, status:{}, response:{}",
                    orderNumber, beforeStatus, status, JSON.toJSONString(response));
            return false;
        }
        return Boolean.TRUE.equals(response.getData());
    }

    private void sendDelayOrderCancel(OrderCreateMq orderCreateMq) {
        DelayOrderCancelDto delayOrderCancelDto = new DelayOrderCancelDto();
        delayOrderCancelDto.setProgramId(orderCreateMq.getProgramId());
        delayOrderCancelDto.setOrderNumber(orderCreateMq.getOrderNumber());
        orderDelayOrderCancelSend.sendMessage(delayOrderCancelDto);
    }

    public String getCache(OrderGetDto orderGetDto) {
        Long currentUserId = currentLoginUserId();
        String cachedOrderNumber = null;
        try {
            cachedOrderNumber = redisCache.get(RedisKeyBuild.createRedisKey(
                    RedisKeyManage.ORDER_MQ, orderGetDto.getOrderNumber()), String.class);
        } catch (RuntimeException e) {
            log.warn("query order cache failed, fallback to db, orderNumber:{}", orderGetDto.getOrderNumber(), e);
        }
        if (!StringUtil.isEmpty(cachedOrderNumber) && noPayOrderExists(orderGetDto.getOrderNumber(), currentUserId)) {
            return cachedOrderNumber;
        }
        Order order = orderMapper.selectOne(Wrappers.lambdaQuery(Order.class)
                .select(Order::getOrderNumber)
                .eq(Order::getOrderNumber, orderGetDto.getOrderNumber())
                .eq(Order::getUserId, currentUserId)
                .eq(Order::getOrderStatus, OrderStatus.NO_PAY.getCode()));
        if (Objects.isNull(order)) {
            return null;
        }
        String orderNumber = String.valueOf(order.getOrderNumber());
        cacheCreatedOrderNumberSafely(orderNumber);
        return orderNumber;
    }

    private boolean noPayOrderExists(Long orderNumber, Long currentUserId) {
        Long count = orderMapper.selectCount(Wrappers.lambdaQuery(Order.class)
                .eq(Order::getOrderNumber, orderNumber)
                .eq(Order::getUserId, currentUserId)
                .eq(Order::getOrderStatus, OrderStatus.NO_PAY.getCode()));
        return count != null && count > 0;
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
        checkOrderOwner(order, currentLoginUserId());
        if (!Objects.equals(order.getOrderStatus(), OrderStatus.NO_PAY.getCode())) {
            throw new TikectsystemFrameException(BaseCode.CAN_NOT_CANCEL);
        }
        return cancel(orderCancelDto);
    }

    private Long currentLoginUserId() {
        String userId = BaseParameterHolder.getParameter(USER_ID);
        if (StringUtil.isEmpty(userId)) {
            throw new TikectsystemFrameException(BaseCode.USER_ID_EMPTY);
        }
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException e) {
            throw new TikectsystemFrameException(BaseCode.USER_ID_EMPTY);
        }
    }

    private void checkOrderOwner(Order order, Long currentUserId) {
        if (Objects.isNull(order) || !Objects.equals(order.getUserId(), currentUserId)) {
            throw new TikectsystemFrameException(BaseCode.ORDER_NOT_EXIST);
        }
    }


    public void delOrderAndOrderTicketUser(){
        orderMapper.relDelOrder();
        orderTicketUserMapper.relDelOrderTicketUser();
        orderTicketUserRecordMapper.relDelOrderTicketUserRecord();
        orderProgramMapper.relDelOrderProgram();
    }
}
