package com.tikectsystem.service;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tikectsystem.client.BaseDataClient;
import com.tikectsystem.common.ApiResponse;
import com.tikectsystem.core.RedisKeyManage;
import com.tikectsystem.dto.GetChannelDataByCodeDto;
import com.tikectsystem.dto.UserAuthenticationDto;
import com.tikectsystem.dto.UserExistDto;
import com.tikectsystem.dto.UserGetAndTicketUserListDto;
import com.tikectsystem.dto.UserIdDto;
import com.tikectsystem.dto.UserLoginDto;
import com.tikectsystem.dto.UserLogoutDto;
import com.tikectsystem.dto.UserMobileDto;
import com.tikectsystem.dto.UserRegisterDto;
import com.tikectsystem.dto.UserUpdateDto;
import com.tikectsystem.dto.UserUpdateEmailDto;
import com.tikectsystem.dto.UserUpdateMobileDto;
import com.tikectsystem.dto.UserUpdatePasswordDto;
import com.tikectsystem.entity.TicketUser;
import com.tikectsystem.entity.User;
import com.tikectsystem.entity.UserEmail;
import com.tikectsystem.entity.UserMobile;
import com.tikectsystem.enums.BaseCode;
import com.tikectsystem.enums.BusinessStatus;
import com.tikectsystem.enums.CompositeCheckType;
import com.tikectsystem.exception.TikectsystemFrameException;
import com.tikectsystem.handler.BloomFilterHandler;
import com.tikectsystem.initialize.impl.composite.CompositeContainer;
import com.tikectsystem.jwt.TokenUtil;
import com.tikectsystem.mapper.TicketUserMapper;
import com.tikectsystem.mapper.UserEmailMapper;
import com.tikectsystem.mapper.UserMapper;
import com.tikectsystem.mapper.UserMobileMapper;
import com.tikectsystem.redis.RedisCache;
import com.tikectsystem.redis.RedisKeyBuild;
import com.tikectsystem.servicelock.LockType;
import com.tikectsystem.servicelock.annotion.ServiceLock;
import com.tikectsystem.util.StringUtil;
import com.tikectsystem.vo.GetChannelDataVo;
import com.tikectsystem.vo.TicketUserVo;
import com.tikectsystem.vo.UserGetAndTicketUserListVo;
import com.tikectsystem.vo.UserLoginVo;
import com.tikectsystem.vo.UserVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.tikectsystem.core.DistributedLockConstants.REGISTER_USER_LOCK;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 用户 service
 * @author: 阿星不是程序员
 **/
@Slf4j
@Service
public class UserService extends ServiceImpl<UserMapper, User> {
    
    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private UserMobileMapper userMobileMapper;
    
    @Autowired
    private UserEmailMapper userEmailMapper;
    
    @Autowired
    private UidGenerator uidGenerator;
    
    @Autowired
    private RedisCache redisCache;
    
    @Autowired
    private TicketUserMapper ticketUserMapper;
    
    @Autowired
    private BloomFilterHandler bloomFilterHandler;
    
    @Autowired
    private CompositeContainer compositeContainer;
    
    @Autowired
    private BaseDataClient baseDataClient;
    
    @Value("${token.expire.time:40}")
    private Long tokenExpireTime;
    
    private static final Integer ERROR_COUNT_THRESHOLD = 5;
    
    @Transactional(rollbackFor = Exception.class)
    @ServiceLock(lockType= LockType.Write,name = REGISTER_USER_LOCK,keys = {"#userRegisterDto.mobile"})
    public Boolean register(UserRegisterDto userRegisterDto) {
        compositeContainer.execute(CompositeCheckType.USER_REGISTER_CHECK.getValue(),userRegisterDto);
        log.info("注册手机号:{}",userRegisterDto.getMobile());
        //用户表添加
        User user = new User();
        BeanUtils.copyProperties(userRegisterDto,user);
        user.setId(uidGenerator.getUid());
        userMapper.insert(user);
        //用户手机表添加
        UserMobile userMobile = new UserMobile();
        userMobile.setId(uidGenerator.getUid());
        userMobile.setUserId(user.getId());
        userMobile.setMobile(userRegisterDto.getMobile());
        userMobileMapper.insert(userMobile);
        bloomFilterHandler.add(userMobile.getMobile());
        return true;
    }
    
    @ServiceLock(lockType= LockType.Read,name = REGISTER_USER_LOCK,keys = {"#mobile"})
    public void exist(UserExistDto userExistDto){
        doExist(userExistDto.getMobile());
    }
    
    public void doExist(String mobile){
        boolean contains = bloomFilterHandler.contains(mobile);
        if (contains) {
            LambdaQueryWrapper<UserMobile> queryWrapper = Wrappers.lambdaQuery(UserMobile.class)
                    .eq(UserMobile::getMobile, mobile);
            UserMobile userMobile = userMobileMapper.selectOne(queryWrapper);
            if (Objects.nonNull(userMobile)) {
                throw new TikectsystemFrameException(BaseCode.USER_EXIST);
            }
        }
    }

    /**
     * 登录
     * @param userLoginDto 登录入参
     * @return 用户信息
     * */
    public UserLoginVo login(UserLoginDto userLoginDto) {
        UserLoginVo userLoginVo = new UserLoginVo();
        String code = userLoginDto.getCode();
        String mobile = userLoginDto.getMobile();
        String email = userLoginDto.getEmail();
        String password = userLoginDto.getPassword();
        //如果手机号和邮箱同时不存在，那么直接抛出异常
        if (StringUtil.isEmpty(mobile) && StringUtil.isEmpty(email)) {
            throw new TikectsystemFrameException(BaseCode.USER_MOBILE_AND_EMAIL_NOT_EXIST);
        }
        Long userId;
        if (StringUtil.isNotEmpty(mobile)) {
            //检查输入的手机号是否达到输入错误次数限制
            String errorCountStr =
                    redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.LOGIN_USER_MOBILE_ERROR, mobile), String.class);
            //如果达到限制的阈值则不再往下执行
            if (StringUtil.isNotEmpty(errorCountStr) && Integer.parseInt(errorCountStr) >= ERROR_COUNT_THRESHOLD) {
                throw new TikectsystemFrameException(BaseCode.MOBILE_ERROR_COUNT_TOO_MANY);
            }
            //如果手机号存在，则用手机号查询用户id
            LambdaQueryWrapper<UserMobile> queryWrapper = Wrappers.lambdaQuery(UserMobile.class)
                    .eq(UserMobile::getMobile, mobile);
            UserMobile userMobile = userMobileMapper.selectOne(queryWrapper);
            if (Objects.isNull(userMobile)) {
                //如果查询手机号不存在，则放入redis中将手机号输入错误的计数器加1
                redisCache.incrBy(RedisKeyBuild.createRedisKey(RedisKeyManage.LOGIN_USER_MOBILE_ERROR,mobile),1);
                redisCache.expire(RedisKeyBuild.createRedisKey(RedisKeyManage.LOGIN_USER_MOBILE_ERROR,mobile),1,TimeUnit.MINUTES);
                throw new TikectsystemFrameException(BaseCode.USER_MOBILE_EMPTY);
            }
            userId = userMobile.getUserId();
        }else {
            //检查输入的邮箱是否达到输入错误次数限制
            String errorCountStr =
                    redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.LOGIN_USER_EMAIL_ERROR, email), String.class);
            //如果达到限制的阈值则不再往下执行
            if (StringUtil.isNotEmpty(errorCountStr) && Integer.parseInt(errorCountStr) >= ERROR_COUNT_THRESHOLD) {
                throw new TikectsystemFrameException(BaseCode.EMAIL_ERROR_COUNT_TOO_MANY);
            }
            //用邮箱查询用户id
            LambdaQueryWrapper<UserEmail> queryWrapper = Wrappers.lambdaQuery(UserEmail.class)
                    .eq(UserEmail::getEmail, email);
            UserEmail userEmail = userEmailMapper.selectOne(queryWrapper);
            if (Objects.isNull(userEmail)) {
                //如果查询手机号不存在，则放入redis中将手机号输入错误的计数器加1
                redisCache.incrBy(RedisKeyBuild.createRedisKey(RedisKeyManage.LOGIN_USER_EMAIL_ERROR,email),1);
                redisCache.expire(RedisKeyBuild.createRedisKey(RedisKeyManage.LOGIN_USER_EMAIL_ERROR,email),1,TimeUnit.MINUTES);
                throw new TikectsystemFrameException(BaseCode.USER_EMAIL_NOT_EXIST);
            }
            userId = userEmail.getUserId();
        }
        //从库中查询用户
        LambdaQueryWrapper<User> queryUserWrapper = Wrappers.lambdaQuery(User.class)
                .eq(User::getId, userId).eq(User::getPassword, password);
        User user = userMapper.selectOne(queryUserWrapper);
        //用户不存在，抛出异常
        if (Objects.isNull(user)) {
            throw new TikectsystemFrameException(BaseCode.NAME_PASSWORD_ERROR);
        }
        //将用户信息放到缓存中
        redisCache.set(RedisKeyBuild.createRedisKey(RedisKeyManage.USER_LOGIN,code,user.getId()),user,
                tokenExpireTime,TimeUnit.MINUTES);
        userLoginVo.setUserId(userId);
        //生成token
        userLoginVo.setToken(createToken(user.getId(),getChannelDataByCode(code).getTokenSecret()));
        return userLoginVo;
    }
    
    private GetChannelDataVo getChannelDataByRedis(String code){
        return redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.CHANNEL_DATA,code),GetChannelDataVo.class);
    }
    
    private GetChannelDataVo getChannelDataByClient(String code){
        GetChannelDataByCodeDto getChannelDataByCodeDto = new GetChannelDataByCodeDto();
        getChannelDataByCodeDto.setCode(code);
        ApiResponse<GetChannelDataVo> getChannelDataApiResponse = baseDataClient.getByCode(getChannelDataByCodeDto);
        if (getChannelDataApiResponse != null && Objects.equals(getChannelDataApiResponse.getCode(), BaseCode.SUCCESS.getCode()) &&
                Objects.nonNull(getChannelDataApiResponse.getData())) {
            return getChannelDataApiResponse.getData();
        }
        throw new TikectsystemFrameException(BaseCode.CHANNEL_DATA_NOT_EXIST);
    }
    
    public String createToken(Long userId,String tokenSecret){
        Map<String,Object> map = new HashMap<>(4);
        map.put("userId",userId);
        return TokenUtil.createToken(String.valueOf(uidGenerator.getUid()), JSON.toJSONString(map),tokenExpireTime * 60 * 1000,tokenSecret);
    }
    
    public Boolean logout(UserLogoutDto userLogoutDto) {
        String userStr = TokenUtil.parseToken(userLogoutDto.getToken(),getChannelDataByCode(userLogoutDto.getCode())
                .getTokenSecret());
        if (StringUtil.isEmpty(userStr)) {
            throw new TikectsystemFrameException(BaseCode.USER_EMPTY);
        }
        String userId = parseUserId(userStr);
        if (StringUtil.isEmpty(userId)) {
            throw new TikectsystemFrameException(BaseCode.USER_EMPTY);
        }
        redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.USER_LOGIN,userLogoutDto.getCode(),userId));
        return true;
    }

    private String parseUserId(String userStr) {
        try {
            JSONObject user = JSONObject.parseObject(userStr);
            return user == null ? null : user.getString("userId");
        } catch (Exception e) {
            log.warn("parse user id from token subject failed", e);
            return null;
        }
    }
    
    public GetChannelDataVo getChannelDataByCode(String code){
        GetChannelDataVo channelDataVo = getChannelDataByRedis(code);
        if (Objects.isNull(channelDataVo)) {
            channelDataVo = getChannelDataByClient(code);
        }
        return channelDataVo;
    }
    
    @Transactional(rollbackFor = Exception.class)
    public void update(UserUpdateDto userUpdateDto){
        User user = userMapper.selectById(userUpdateDto.getId());
        if (Objects.isNull(user)) {
            throw new TikectsystemFrameException(BaseCode.USER_EMPTY);
        }
        User updateUser = new User();
        BeanUtil.copyProperties(userUpdateDto,updateUser);
        userMapper.updateById(updateUser);
    }
    @Transactional(rollbackFor = Exception.class)
    public void updatePassword(UserUpdatePasswordDto userUpdatePasswordDto){
        User user = userMapper.selectById(userUpdatePasswordDto.getId());
        if (Objects.isNull(user)) {
            throw new TikectsystemFrameException(BaseCode.USER_EMPTY);
        }
        User updateUser = new User();
        BeanUtil.copyProperties(userUpdatePasswordDto,updateUser);
        userMapper.updateById(updateUser);
    }
    @Transactional(rollbackFor = Exception.class)
    public void updateEmail(UserUpdateEmailDto userUpdateEmailDto){
        User user = userMapper.selectById(userUpdateEmailDto.getId());
        if (Objects.isNull(user)) {
            throw new TikectsystemFrameException(BaseCode.USER_EMPTY);
        }
        User updateUser = new User();
        BeanUtil.copyProperties(userUpdateEmailDto,updateUser);
        updateUser.setEmailStatus(BusinessStatus.YES.getCode());
        userMapper.updateById(updateUser);
        
        String oldEmail = user.getEmail();
        LambdaQueryWrapper<UserEmail> userEmailLambdaQueryWrapper = Wrappers.lambdaQuery(UserEmail.class)
                .eq(UserEmail::getEmail, userUpdateEmailDto.getEmail());
        UserEmail userEmail = userEmailMapper.selectOne(userEmailLambdaQueryWrapper);
        if (Objects.isNull(userEmail)) {
            userEmail = new UserEmail();
            userEmail.setId(uidGenerator.getUid());
            userEmail.setUserId(user.getId());
            userEmail.setEmail(userUpdateEmailDto.getEmail());
            userEmailMapper.insert(userEmail);
        }else {
            LambdaUpdateWrapper<UserEmail> userEmailLambdaUpdateWrapper = Wrappers.lambdaUpdate(UserEmail.class)
                    .eq(UserEmail::getEmail, oldEmail);
            UserEmail updateUserEmail = new UserEmail();
            updateUserEmail.setEmail(userUpdateEmailDto.getEmail());
            userEmailMapper.update(updateUserEmail,userEmailLambdaUpdateWrapper);
        }
    }
    
    @Transactional(rollbackFor = Exception.class)
    public void updateMobile(UserUpdateMobileDto userUpdateMobileDto){
        User user = userMapper.selectById(userUpdateMobileDto.getId());
        if (Objects.isNull(user)) {
            throw new TikectsystemFrameException(BaseCode.USER_EMPTY);
        }
        String oldMobile = user.getMobile();
        User updateUser = new User();
        BeanUtil.copyProperties(userUpdateMobileDto,updateUser);
        userMapper.updateById(updateUser);
        LambdaQueryWrapper<UserMobile> userMobileLambdaQueryWrapper = Wrappers.lambdaQuery(UserMobile.class)
                .eq(UserMobile::getMobile, userUpdateMobileDto.getMobile());
        UserMobile userMobile = userMobileMapper.selectOne(userMobileLambdaQueryWrapper);
        if (Objects.isNull(userMobile)) {
            userMobile = new UserMobile();
            userMobile.setId(uidGenerator.getUid());
            userMobile.setUserId(user.getId());
            userMobile.setMobile(userUpdateMobileDto.getMobile());
            userMobileMapper.insert(userMobile);
        }else {
            LambdaUpdateWrapper<UserMobile> userMobileLambdaUpdateWrapper = Wrappers.lambdaUpdate(UserMobile.class)
                    .eq(UserMobile::getMobile, oldMobile);
            UserMobile updateUserMobile = new UserMobile();
            updateUserMobile.setMobile(userUpdateMobileDto.getMobile());
            userMobileMapper.update(updateUserMobile,userMobileLambdaUpdateWrapper);
        }
    }
    
    @Transactional(rollbackFor = Exception.class)
    public void authentication(UserAuthenticationDto userAuthenticationDto){
        User user = userMapper.selectById(userAuthenticationDto.getId());
        if (Objects.isNull(user)) {
            throw new TikectsystemFrameException(BaseCode.USER_EMPTY);
        }
        if (Objects.equals(user.getRelAuthenticationStatus(), BusinessStatus.YES.getCode())) {
            throw new TikectsystemFrameException(BaseCode.USER_AUTHENTICATION);
        }
        User updateUser = new User();
        updateUser.setId(user.getId());
        updateUser.setRelName(userAuthenticationDto.getRelName());
        updateUser.setIdNumber(userAuthenticationDto.getIdNumber());
        updateUser.setRelAuthenticationStatus(BusinessStatus.YES.getCode());
        userMapper.updateById(updateUser);
    }
    
    public UserVo getByMobile(UserMobileDto userMobileDto) {
        LambdaQueryWrapper<UserMobile> queryWrapper = Wrappers.lambdaQuery(UserMobile.class)
                .eq(UserMobile::getMobile, userMobileDto.getMobile());
        UserMobile userMobile = userMobileMapper.selectOne(queryWrapper);
        if (Objects.isNull(userMobile)) {
            throw new TikectsystemFrameException(BaseCode.USER_MOBILE_EMPTY);
        }
        User user = userMapper.selectById(userMobile.getUserId());
        if (Objects.isNull(user)) {
            throw new TikectsystemFrameException(BaseCode.USER_EMPTY);
        }
        UserVo userVo = new UserVo();
        BeanUtil.copyProperties(user,userVo);
        userVo.setMobile(userMobile.getMobile());
        return userVo;
    }
    
    public UserVo getById(UserIdDto userIdDto) {
        User user = userMapper.selectById(userIdDto.getId());
        if (Objects.isNull(user)) {
            throw new TikectsystemFrameException(BaseCode.USER_EMPTY);
        }
        UserVo userVo = new UserVo();
        BeanUtil.copyProperties(user,userVo);
        return userVo;
    }
    
    public UserGetAndTicketUserListVo getUserAndTicketUserList(final UserGetAndTicketUserListDto userGetAndTicketUserListDto) {
        UserIdDto userIdDto = new UserIdDto();
        userIdDto.setId(userGetAndTicketUserListDto.getUserId());
        UserVo userVo = getById(userIdDto);
        
        LambdaQueryWrapper<TicketUser> ticketUserLambdaQueryWrapper = Wrappers.lambdaQuery(TicketUser.class)
                .eq(TicketUser::getUserId, userGetAndTicketUserListDto.getUserId());
        List<TicketUser> ticketUserList = ticketUserMapper.selectList(ticketUserLambdaQueryWrapper);
        List<TicketUserVo> ticketUserVoList = BeanUtil.copyToList(ticketUserList, TicketUserVo.class);
        
        UserGetAndTicketUserListVo userGetAndTicketUserListVo = new UserGetAndTicketUserListVo();
        userGetAndTicketUserListVo.setUserVo(userVo);
        userGetAndTicketUserListVo.setTicketUserVoList(ticketUserVoList);
        return userGetAndTicketUserListVo;
    }
    
    public List<String> getAllMobile(){
        QueryWrapper<User> lambdaQueryWrapper = Wrappers.emptyWrapper();
        List<User> users = userMapper.selectList(lambdaQueryWrapper);
        return users.stream().map(User::getMobile).collect(Collectors.toList());
    }
}
