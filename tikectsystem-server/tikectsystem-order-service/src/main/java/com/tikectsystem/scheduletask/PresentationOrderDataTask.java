package com.tikectsystem.scheduletask;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.http.HttpRequest;
import com.alibaba.fastjson.JSON;
import com.tikectsystem.core.RedisKeyManage;
import com.tikectsystem.domain.DiscardOrder;
import com.tikectsystem.domain.OrderCreateMq;
import com.tikectsystem.dto.OrderTicketUserCreateDto;
import com.tikectsystem.dto.ProgramGetDto;
import com.tikectsystem.dto.ProgramOrderCreateDto;
import com.tikectsystem.dto.TicketUserListDto;
import com.tikectsystem.dto.UserLoginDto;
import com.tikectsystem.enums.BaseCode;
import com.tikectsystem.enums.DiscardOrderReason;
import com.tikectsystem.redis.RedisCache;
import com.tikectsystem.redis.RedisKeyBuild;
import com.tikectsystem.service.OrderService;
import com.tikectsystem.simulation.module.CreateProgramOrderResultModule;
import com.tikectsystem.simulation.module.ProgramDetailResultModule;
import com.tikectsystem.simulation.module.TickerUserListResultModule;
import com.tikectsystem.simulation.module.UserLoginResultModule;
import com.tikectsystem.util.DateUtils;
import com.tikectsystem.util.StringUtil;
import com.tikectsystem.vo.ProgramVo;
import com.tikectsystem.vo.ProgramOrderCreateVo;
import com.tikectsystem.vo.TicketUserVo;
import com.tikectsystem.vo.UserLoginVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.tikectsystem.simulation.constant.SimulationOrderConstant.CREATE_PROGRAM_ORDER_URL;
import static com.tikectsystem.simulation.constant.SimulationOrderConstant.PROGRAM_DETAIL_URL;
import static com.tikectsystem.simulation.constant.SimulationOrderConstant.TICKET_USER_LIST_URL;
import static com.tikectsystem.simulation.constant.SimulationOrderConstant.USER_LOGIN_URL;
import static com.tikectsystem.constant.Constant.CODE;
import static com.tikectsystem.constant.Constant.USER_ID;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 订单服务定时任务重置
 * @author: 阿星不是程序员
 **/
@Slf4j
@Component
public class PresentationOrderDataTask {
    
    @Autowired
    private OrderService orderService;
    
    @Autowired
    private RedisCache redisCache;

    @Autowired
    private OrderBackgroundTaskExecutor orderBackgroundTaskExecutor;

    @Value("${presentation.order.enabled:false}")
    private Boolean presentationOrderEnabled;

    @Value("${presentation.order.login.code:0001}")
    private String presentationOrderLoginCode;

    @Value("${presentation.order.login.mobile:13154982525}")
    private String presentationOrderLoginMobile;

    @Value("${presentation.order.login.password:}")
    private String presentationOrderLoginPassword;
    
    @Scheduled(cron = "0 30 23 * * ?")
    public void executeTask(){
        if (!Boolean.TRUE.equals(presentationOrderEnabled)) {
            log.info("订单演示数据重置任务未启用");
            return;
        }
        if (StringUtil.isEmpty(presentationOrderLoginPassword)) {
            log.error("订单演示数据重置任务缺少登录密码配置");
            return;
        }
        orderBackgroundTaskExecutor.execute( () -> {
            try {
                log.info("订单服务定时任务重置执行");
                //真实删除所有的订单和购票人订单，购票人订单记录(大麦普通版本没有这步)
                orderService.delOrderAndOrderTicketUser();
                //模拟创建订单
                simulationCreateOrder();
                //将原有的模拟废弃订单数据删除
                redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.DISCARD_ORDER, 34));
                //模拟废弃订单数据，放入Redis中
                redisCache.leftPushForList(RedisKeyBuild.createRedisKey(RedisKeyManage.DISCARD_ORDER, 34),
                        simulationDiscardOrder());
            }catch (Exception e) {
                log.error("executeTask error",e);
            }
        });
    }
    
    /**
     * 模拟废弃订单数据
     */
    private DiscardOrder simulationDiscardOrder(){
        //模拟废弃订单数据
        OrderCreateMq orderCreateMq = new OrderCreateMq();
        orderCreateMq.setCreateOrderTime(DateUtils.now());
        orderCreateMq.setIdentifierId(1421864797540605952L);
        orderCreateMq.setOrderNumber(1965791442215448582L);
        orderCreateMq.setOrderPrice(new BigDecimal(2000));
        orderCreateMq.setOrderVersion(4);
        orderCreateMq.setProgramId(34L);
        orderCreateMq.setProgramItemPicture("https://s21.ax1x.com/2024/06/07/pkYzl9J.jpg");
        orderCreateMq.setProgramPermitChooseSeat(0);
        orderCreateMq.setProgramPlace("工人体育馆");
        orderCreateMq.setProgramShowTime(DateUtils.addWeek(DateUtils.addHour(DateUtils.now(), -4), 1));
        orderCreateMq.setProgramTitle("周杰伦“嘉年华”世界巡回演唱会");
        orderCreateMq.setUserId(1421653760027484162L);
        
        List<OrderTicketUserCreateDto> orderTicketUserCreateDtoList = new ArrayList<>();
        OrderTicketUserCreateDto orderTicketUserCreateDto = new OrderTicketUserCreateDto();
        orderTicketUserCreateDto.setCreateOrderTime(DateUtils.now());
        orderTicketUserCreateDto.setOrderNumber(1965791442215448582L);
        orderTicketUserCreateDto.setOrderPrice(new BigDecimal(2000));
        orderTicketUserCreateDto.setProgramId(34L);
        orderTicketUserCreateDto.setSeatId(10251L);
        orderTicketUserCreateDto.setSeatInfo("551排1列");
        orderTicketUserCreateDto.setTicketCategoryId(46L);
        orderTicketUserCreateDto.setTicketUserId(1421653760027500032L);
        orderTicketUserCreateDto.setUserId(1421653760027484162L);
        orderTicketUserCreateDtoList.add(orderTicketUserCreateDto);
        orderCreateMq.setOrderTicketUserCreateDtoList(orderTicketUserCreateDtoList);
        
        return new DiscardOrder(orderCreateMq, DiscardOrderReason.CONSUMER_DELAY.getCode());
    }
    
    
    public void simulationCreateOrder(){
        try {
            //先登录
            UserLoginDto userLoginDto = new UserLoginDto();
            userLoginDto.setCode(presentationOrderLoginCode);
            userLoginDto.setMobile(presentationOrderLoginMobile);
            userLoginDto.setPassword(presentationOrderLoginPassword);
            UserLoginVo userLoginVo = userLoginHttp(userLoginDto);
            if (Objects.isNull(userLoginVo)) {
                log.error("模拟用户登录失败 code:{}, mobile:{}", presentationOrderLoginCode, presentationOrderLoginMobile);
                return;
            }
            //获取购票人列表
            TicketUserListDto ticketUserListDto = new TicketUserListDto();
            ticketUserListDto.setUserId(userLoginVo.getUserId());
            List<TicketUserVo> ticketUserVoList = tickerUserListHttp(ticketUserListDto,userLoginVo);
            if (CollectionUtil.isEmpty(ticketUserVoList)){
                log.error("模拟获取购票人列表失败 ticketUserListDto:{}",JSON.toJSONString(ticketUserListDto));
                return;
            }
            //获取节目详情
            ProgramGetDto programGetDto = new ProgramGetDto();
            //这里固定使用 周杰伦“嘉年华”世界巡回演唱会 这个节目ID
            programGetDto.setId(34L);
            ProgramVo programVo = programDetailHttp(programGetDto,userLoginVo);
            if (Objects.isNull(programVo)) {
                log.error("模拟获取节目详情失败 programGetDto:{}",JSON.toJSONString(programGetDto));
                return;
            }
            //获取第一个票档id
            ProgramOrderCreateDto programOrderCreateDto = getProgramOrderCreateDto(programVo, userLoginVo,
                    ticketUserVoList);
            if (programOrderCreateDto == null) {
                log.error("simulation build order param fail, programVo:{}, userLoginVo:{}, ticketUserVoList:{}",
                        JSON.toJSONString(programVo), JSON.toJSONString(userLoginVo), JSON.toJSONString(ticketUserVoList));
                return;
            }
            String orderNumber = createProgramOrder(programOrderCreateDto,userLoginVo);
            if (StringUtil.isEmpty(orderNumber)) {
                log.error("模拟创建订单失败 programOrderCreateDto:{}",JSON.toJSONString(programOrderCreateDto));
            }else {
                log.info("模拟创建订单成功 orderNumber:{}",orderNumber);
            }
        }catch (Exception e) {
            log.error("simulationCreateOrder error",e);   
        }
    }
    
    private static ProgramOrderCreateDto getProgramOrderCreateDto(ProgramVo programVo, UserLoginVo userLoginVo, 
                                                                   List<TicketUserVo> ticketUserVoList) {
        if (programVo == null || userLoginVo == null || CollectionUtil.isEmpty(programVo.getTicketCategoryVoList()) ||
                CollectionUtil.isEmpty(ticketUserVoList)) {
            return null;
        }
        Long ticketCategoryId = programVo.getTicketCategoryVoList().get(0).getId();
        //创建订单
        ProgramOrderCreateDto programOrderCreateDto = new ProgramOrderCreateDto();
        programOrderCreateDto.setUserId(userLoginVo.getUserId());
        programOrderCreateDto.setRequestId("presentation-" + userLoginVo.getUserId() + "-" + System.currentTimeMillis());
        programOrderCreateDto.setProgramId(programVo.getId());
        List<Long> ticketUserIdList = new ArrayList<>();
        ticketUserIdList.add(ticketUserVoList.get(0).getId());
        programOrderCreateDto.setTicketUserIdList(ticketUserIdList);
        programOrderCreateDto.setTicketCategoryId(ticketCategoryId);
        programOrderCreateDto.setTicketCount(1);
        return programOrderCreateDto;
    }
    
    public UserLoginVo userLoginHttp(UserLoginDto userLoginDto){
        String result = buildSimulationRequest(USER_LOGIN_URL,userLoginDto,null)
                .execute().body();
        UserLoginResultModule userLoginResultModule = JSON.parseObject(result, UserLoginResultModule.class);
        if (userLoginResultModule == null || !Objects.equals(userLoginResultModule.getCode(), BaseCode.SUCCESS.getCode())) {
            return null;
        }
        return userLoginResultModule.getData();
    }
    
    public List<TicketUserVo> tickerUserListHttp(TicketUserListDto ticketUserListDto,UserLoginVo userLoginVo){
        String result = buildSimulationRequest(TICKET_USER_LIST_URL,ticketUserListDto,userLoginVo)
                .execute().body();
        TickerUserListResultModule tickerUserListResultModule = JSON.parseObject(result, TickerUserListResultModule.class);
        if (tickerUserListResultModule == null || !Objects.equals(tickerUserListResultModule.getCode(), BaseCode.SUCCESS.getCode())) {
            return null;
        }
        return tickerUserListResultModule.getData();
    }
    
    public ProgramVo programDetailHttp(ProgramGetDto programGetDto,UserLoginVo userLoginVo){
        String result = buildSimulationRequest(PROGRAM_DETAIL_URL,programGetDto,userLoginVo)
                .execute().body();
        ProgramDetailResultModule programDetailResultModule = JSON.parseObject(result, ProgramDetailResultModule.class);
        if (programDetailResultModule == null || !Objects.equals(programDetailResultModule.getCode(), BaseCode.SUCCESS.getCode())) {
            return null;
        }
        return programDetailResultModule.getData();
    }
    
    public String createProgramOrder(ProgramOrderCreateDto programOrderCreateDto,UserLoginVo userLoginVo){
        String result = buildSimulationRequest(CREATE_PROGRAM_ORDER_URL,programOrderCreateDto,userLoginVo)
                .execute().body();
        CreateProgramOrderResultModule createProgramOrderResultModule = JSON.parseObject(result, CreateProgramOrderResultModule.class);
        if (createProgramOrderResultModule == null || !Objects.equals(createProgramOrderResultModule.getCode(), BaseCode.SUCCESS.getCode())) {
            return null;
        }
        ProgramOrderCreateVo data = createProgramOrderResultModule.getData();
        return data == null ? null : data.getOrderNumber();
        
    }

    private HttpRequest buildSimulationRequest(String url,Object body,UserLoginVo userLoginVo) {
        HttpRequest request = HttpRequest.post(url)
                .timeout(20000)
                .header(CODE,presentationOrderLoginCode)
                .body(JSON.toJSONString(body));
        if (userLoginVo != null && userLoginVo.getUserId() != null) {
            request.header(USER_ID,String.valueOf(userLoginVo.getUserId()));
        }
        return request;
    }
}
