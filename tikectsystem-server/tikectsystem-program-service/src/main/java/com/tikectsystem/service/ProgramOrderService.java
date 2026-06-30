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
import com.tikectsystem.dto.OrderTicketUserCreateDto;
import com.tikectsystem.dto.ProgramOrderCreateDto;
import com.tikectsystem.dto.SeatDto;
import com.tikectsystem.entity.ProgramRecordTask;
import com.tikectsystem.entity.ProgramShowTime;
import com.tikectsystem.enums.BaseCode;
import com.tikectsystem.enums.OrderStatus;
import com.tikectsystem.enums.RecordType;
import com.tikectsystem.enums.SellStatus;
import com.tikectsystem.exception.TikectsystemFrameException;
import com.tikectsystem.mapper.ProgramRecordTaskMapper;
import com.tikectsystem.redis.RedisKeyBuild;
import com.tikectsystem.service.delaysend.DelayOrderCancelSend;
import com.tikectsystem.service.domain.CreateOrderTemporaryData;
import com.tikectsystem.service.executor.ProgramRecordTaskExecutor;
import com.tikectsystem.service.kafka.CreateOrderMqDomain;
import com.tikectsystem.service.kafka.CreateOrderSend;
import com.tikectsystem.service.lua.ProgramCacheCreateOrderData;
import com.tikectsystem.service.lua.ProgramCacheCreateOrderResolutionOperate;
import com.tikectsystem.service.lua.ProgramCacheResolutionOperate;
import com.tikectsystem.service.tool.SeatMatch;
import com.tikectsystem.util.DateUtils;
import com.tikectsystem.util.StringUtil;
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
import static com.tikectsystem.constant.ProgramOrderConstant.ORDER_TABLE_COUNT;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 节目订单 service
 * @author: 阿星不是程序员
 **/
@Slf4j
@Service
public class ProgramOrderService {

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
        updateProgramCacheDataResolution(programOrderCreateDto.getProgramId(), purchaseSeatList, OrderStatus.NO_PAY);
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
        Long programId = programOrderCreateDto.getProgramId();
        List<SeatDto> seatDtoList = programOrderCreateDto.getSeatDtoList();
        List<String> keys = new ArrayList<>();
        String[] data = new String[3];
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
                jsonObject.put("ticketCategoryId", ticketCategoryId);
                //扣减余票数量
                jsonObject.put("ticketCount", ticketCount);
                jsonArray.add(jsonObject);

                JSONObject seatDatajsonObject = new JSONObject();
                //未售卖座位的hash的key
                seatDatajsonObject.put("seatNoSoldHashKey", RedisKeyBuild.createRedisKey(
                        RedisKeyManage.PROGRAM_SEAT_NO_SOLD_RESOLUTION_HASH, programId, ticketCategoryId).getRelKey());
                //座位数据
                seatDatajsonObject.put("seatDataList", JSON.toJSONString(entry.getValue()));
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
            jsonObject.put("ticketCategoryId", ticketCategoryId);
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
        data[0] = JSON.toJSONString(jsonArray);
        data[1] = JSON.toJSONString(addSeatDatajsonArray);
        //购票人id集合传给 Lua 时必须使用字符串，避免 cjson 按 number 处理大 Long 导致精度丢失。
        data[2] = JSON.toJSONString(programOrderCreateDto.getTicketUserIdList().stream()
                .map(String::valueOf).collect(Collectors.toList()));
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
        return new CreateOrderTemporaryData(identifierId, programCacheCreateOrderData.getPurchaseSeatList());
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
        if (CollectionUtil.isEmpty(purchaseSeatList)) {
            throw new TikectsystemFrameException(BaseCode.SEAT_NOT_EXIST);
        }
        ProgramVo programVo = programService.simpleGetProgramAndShowMultipleCache(programId);
        OrderCreateDto orderCreateDto = new OrderCreateDto();
        orderCreateDto.setOrderNumber(uidGenerator.getOrderNumber(userId, ORDER_TABLE_COUNT));
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

    private void updateProgramCacheDataResolution(Long programId, List<SeatVo> seatVoList, OrderStatus orderStatus) {
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
            //如果是订单创建，那么就扣除未售卖的座位id
            //如果是订单取消，那么就扣除锁定的座位id
            delSeatIdjsonObject.put("seatIdList", v.stream().map(SeatVo::getId).map(String::valueOf).collect(Collectors.toList()));
            delSeatIdjsonArray.add(delSeatIdjsonObject);
            //要进行添加座位的key
            seatDatajsonObject.put("seatHashKeyAdd", seatHashKeyAdd);
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
        programCacheResolutionOperate.programCacheOperate(keys, data);
    }
}
