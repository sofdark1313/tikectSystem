package com.tikectsystem.filter;


import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSON;
import com.baidu.fsg.uid.UidGenerator;
import com.tikectsystem.conf.RequestTemporaryWrapper;
import com.tikectsystem.enums.BaseCode;
import com.tikectsystem.exception.ArgumentError;
import com.tikectsystem.exception.ArgumentException;
import com.tikectsystem.exception.TikectsystemFrameException;
import com.tikectsystem.pro.limit.RateLimiter;
import com.tikectsystem.pro.limit.RateLimiterProperty;
import com.tikectsystem.property.GatewayProperty;
import com.tikectsystem.service.ApiRestrictService;
import com.tikectsystem.service.ChannelDataService;
import com.tikectsystem.service.TokenService;
import com.tikectsystem.threadlocal.BaseParameterHolder;
import com.tikectsystem.util.RsaSignTool;
import com.tikectsystem.util.RsaTool;
import com.tikectsystem.util.StringUtil;
import com.tikectsystem.vo.GetChannelDataVo;
import com.tikectsystem.vo.UserVo;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.factory.rewrite.CachedBodyOutputMessage;
import org.springframework.cloud.gateway.support.BodyInserterContext;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static com.tikectsystem.constant.Constant.GRAY_PARAMETER;
import static com.tikectsystem.constant.Constant.TRACE_ID;
import static com.tikectsystem.constant.GatewayConstant.BUSINESS_BODY;
import static com.tikectsystem.constant.GatewayConstant.CODE;
import static com.tikectsystem.constant.GatewayConstant.ENCRYPT;
import static com.tikectsystem.constant.GatewayConstant.NO_VERIFY;
import static com.tikectsystem.constant.GatewayConstant.REQUEST_BODY;
import static com.tikectsystem.constant.GatewayConstant.TOKEN;
import static com.tikectsystem.constant.GatewayConstant.USER_ID;
import static com.tikectsystem.constant.GatewayConstant.V2;
import static com.tikectsystem.constant.GatewayConstant.VERIFY_VALUE;

@Component
@Slf4j
public class RequestValidationFilter implements GlobalFilter, Ordered {

    private static final PathMatcher PATH_MATCHER = new AntPathMatcher();

    @Autowired
    private ServerCodecConfigurer serverCodecConfigurer;

    @Autowired
    private ChannelDataService channelDataService;

    @Autowired
    private ApiRestrictService apiRestrictService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private GatewayProperty gatewayProperty;

    @Autowired
    private UidGenerator uidGenerator;

    @Autowired
    private RateLimiterProperty rateLimiterProperty;

    @Autowired
    private RateLimiter rateLimiter;


    @Override
    public Mono<Void> filter(final ServerWebExchange exchange, final GatewayFilterChain chain) {
        if (Boolean.TRUE.equals(rateLimiterProperty.getRateSwitch())) {
            boolean acquired = false;
            try {
                rateLimiter.acquire();
                acquired = true;
                return doFilter(exchange,chain).doFinally(signalType -> rateLimiter.release());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("interrupted error",e);
                throw new TikectsystemFrameException(BaseCode.THREAD_INTERRUPTED);
            } catch (RuntimeException e) {
                if (acquired) {
                    rateLimiter.release();
                }
                throw e;
            }
        }else{
            return doFilter(exchange, chain);
        }
    }

    public Mono<Void> doFilter(final ServerWebExchange exchange, final GatewayFilterChain chain){
        ServerHttpRequest request = exchange.getRequest();
        //链路id
        String traceId = request.getHeaders().getFirst(TRACE_ID);
        //灰度标识
        String gray = request.getHeaders().getFirst(GRAY_PARAMETER);
        //是否验证参数
        String noVerify = request.getHeaders().getFirst(NO_VERIFY);
        //如果链路id不存在，那么在这里生成
        if (StringUtil.isEmpty(traceId)) {
            traceId = String.valueOf(uidGenerator.getUid());
        }
        //将链路id放到日志的MDC中便于日志输出
        MDC.put(TRACE_ID,traceId);
        Map<String,String> headMap = new HashMap<>(8);
        headMap.put(TRACE_ID,traceId);
        if (StringUtil.isNotEmpty(gray)) {
            headMap.put(GRAY_PARAMETER,gray);
        }
        if (StringUtil.isNotEmpty(noVerify)) {
            headMap.put(NO_VERIFY,noVerify);
        }
        //将链路id放到ThreadLocal中
        BaseParameterHolder.setParameter(TRACE_ID,traceId);
        //将灰度标识放到ThreadLocal中
        if (StringUtil.isNotEmpty(gray)) {
            BaseParameterHolder.setParameter(GRAY_PARAMETER,gray);
        }
        try {
            //获取请求类型
            MediaType contentType = request.getHeaders().getContentType();
            //application json请求
            if (Objects.nonNull(contentType) && contentType.toString().toLowerCase().contains(MediaType.APPLICATION_JSON_VALUE.toLowerCase())) {
                //如果是json则进行参数验证
                return readBody(exchange,chain,headMap).doFinally(signalType -> clearRequestContext());
            }else {
                //如果不是json请求，则直接执行
                Map<String, String> map = doExecute("", exchange);
                map.remove(REQUEST_BODY);
                map.putAll(headMap);
                ServerHttpRequest mutatedRequest = request.mutate()
                        .headers(httpHeaders -> httpHeaders.setAll(map))
                        .build();
                return chain.filter(exchange.mutate().request(mutatedRequest).build()).doFinally(signalType -> clearRequestContext());
            }
        } catch (RuntimeException e) {
            clearRequestContext();
            throw e;
        }
    }

    private void clearRequestContext() {
        BaseParameterHolder.removeParameterMap();
        MDC.remove(TRACE_ID);
    }

    /**
     * 此方法是根据源码进行修改为了能读取请求体并修改，不是重点，可忽略
     * */
    private Mono<Void> readBody(ServerWebExchange exchange, GatewayFilterChain chain, Map<String,String> headMap){
        log.info("current thread readBody : {}",Thread.currentThread().getName());
        RequestTemporaryWrapper requestTemporaryWrapper = new RequestTemporaryWrapper();

        ServerRequest serverRequest = ServerRequest.create(exchange, serverCodecConfigurer.getReaders());
        Mono<String> modifiedBody = serverRequest
                .bodyToMono(String.class)
                //execute是执行参数验证的方法
                .flatMap(originalBody -> Mono.just(execute(requestTemporaryWrapper,originalBody,exchange)))
                //这个方法是使用post请求，方式也是json，但请求体为空的情况
                .switchIfEmpty(Mono.defer(() -> Mono.just(execute(requestTemporaryWrapper,"",exchange))));

        BodyInserter bodyInserter = BodyInserters.fromPublisher(modifiedBody, String.class);
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(exchange.getRequest().getHeaders());
        headers.remove(HttpHeaders.CONTENT_LENGTH);

        CachedBodyOutputMessage outputMessage = new CachedBodyOutputMessage(exchange, headers);
        return bodyInserter
                .insert(outputMessage, new BodyInserterContext())
                .then(Mono.defer(() -> chain.filter(
                        exchange.mutate().request(decorateHead(exchange, headers, outputMessage, requestTemporaryWrapper, headMap)).build()
                )))
                .onErrorResume((Function<Throwable, Mono<Void>>) throwable -> Mono.error(throwable));
    }

    public String execute(RequestTemporaryWrapper requestTemporaryWrapper,String requestBody,ServerWebExchange exchange){
        //进行业务验证，并将相关参数放入map
        Map<String, String> map = doExecute(requestBody, exchange);
        //这里的map中的数据在doExecute中放入的，有修改后的请求体和要放在请求头中的数据，先拿出请求体用来返回，然后从map中移除，
        //这样map剩下的数据就都是要放入请求头中的了
        String body = map.get(REQUEST_BODY);
        map.remove(REQUEST_BODY);
        requestTemporaryWrapper.setMap(map);
        return body;
    }
    /**
     * 具体进行参数验证的逻辑
     * */
    private Map<String,String> doExecute(String originalBody,ServerWebExchange exchange){
        log.info("current thread verify: {}",Thread.currentThread().getName());
        ServerHttpRequest request = exchange.getRequest();
        //得到请求体
        String requestBody = originalBody;
        Map<String, String> bodyContent = new HashMap<>(32);
        if (StringUtil.isNotEmpty(originalBody)) {
            //请求体转为map结构
            try {
                bodyContent = JSON.parseObject(originalBody, Map.class);
            } catch (RuntimeException e) {
                log.warn("request body json parse error, path: {}",request.getPath().value(),e);
                throw new TikectsystemFrameException(BaseCode.PARAMETER_ERROR);
            }
            if (bodyContent == null) {
                bodyContent = new HashMap<>(32);
            }
        }
        //基础参数code渠道
        String code = null;
        //用户的token
        String token;
        //用户的userId
        String userId = null;
        //请求的路径
        String url = request.getPath().value();
        //是否跳过参数验证的标识
        String noVerify = request.getHeaders().getFirst(NO_VERIFY);
        //是否允许跳过参数验证
        boolean allowNormalAccess = gatewayProperty.isAllowNormalAccess();
        if ((!allowNormalAccess) && (VERIFY_VALUE.equals(noVerify))) {
            throw new TikectsystemFrameException(BaseCode.ONLY_SIGNATURE_ACCESS_IS_ALLOWED);
        }
        //是否跳过参数验证
        if (checkParameter(originalBody,noVerify) && !skipCheckParameter(url)) {

            String encrypt = request.getHeaders().getFirst(ENCRYPT);
            //应用渠道
            code = bodyContent.get(CODE);
            //token
            token = request.getHeaders().getFirst(TOKEN);
            //验证code参数并获取基础参数
            GetChannelDataVo channelDataVo = channelDataService.getChannelDataByCode(code);
            //如果v2版本就要先对参数进行解密
            if (StringUtil.isNotEmpty(encrypt) && V2.equals(encrypt)) {
                //使用rsa私钥进行解密
                String decrypt = RsaTool.decrypt(bodyContent.get(BUSINESS_BODY),channelDataVo.getDataSecretKey());
                //将解密后的请求体替换加密的请求体
                bodyContent.put(BUSINESS_BODY,decrypt);
            }
            //进行签名验证
            boolean checkFlag = RsaSignTool.verifyRsaSign256(bodyContent, channelDataVo.getSignPublicKey());
            if (!checkFlag) {
                throw new TikectsystemFrameException(BaseCode.RSA_SIGN_ERROR);
            }
            //判断是否跳过验证登录的token
            //默认注册和登录接口跳过验证
            boolean skipCheckTokenResult = skipCheckToken(url);
            if (!skipCheckTokenResult && StringUtil.isEmpty(token)) {
                ArgumentError argumentError = new ArgumentError();
                argumentError.setArgumentName(TOKEN);
                argumentError.setMessage("token参数为空");
                List<ArgumentError> argumentErrorList = new ArrayList<>();
                argumentErrorList.add(argumentError);
                throw new ArgumentException(BaseCode.ARGUMENT_EMPTY.getCode(),argumentErrorList);
            }
            //获取用户id
            if (!skipCheckTokenResult) {
                UserVo userVo = tokenService.getUser(token,code,channelDataVo.getTokenSecret());
                userId = userVo.getId();
            }
            //如果上一步没有获取到用户id，并且此url在有token情况下还需要解析出userid，
            //那么就再解析一遍token
            if (StringUtil.isEmpty(userId) && checkNeedUserId(url) && StringUtil.isNotEmpty(token)) {
                UserVo userVo = tokenService.getUser(token,code,channelDataVo.getTokenSecret());
                userId = userVo.getId();
            }

            requestBody = bodyContent.get(BUSINESS_BODY);
        }
        //根据规则对api接口进行防刷限制
        apiRestrictService.apiRestrict(userId,url,request);
        //将修改后的请求体和要传递的请求头参数放入map
        Map<String,String> map = new HashMap<>(4);
        map.put(REQUEST_BODY,requestBody);
        if (StringUtil.isNotEmpty(code)) {
            map.put(CODE,code);
        }
        if (StringUtil.isNotEmpty(userId)) {
            map.put(USER_ID,userId);
        }
        return map;
    }
    /**
     * 将网关层request请求头中的重要参数传递给后续的微服务中
     * 此方法为Gateway源码部分，可忽略
     */
    private ServerHttpRequestDecorator decorateHead(ServerWebExchange exchange, HttpHeaders headers, CachedBodyOutputMessage outputMessage, RequestTemporaryWrapper requestTemporaryWrapper, Map<String,String> headMap){
        return new ServerHttpRequestDecorator(exchange.getRequest()){
            @Override
            public HttpHeaders getHeaders() {
                log.info("current thread getHeaders: {}",Thread.currentThread().getName());
                long contentLength = headers.getContentLength();
                HttpHeaders newHeaders = new HttpHeaders();
                newHeaders.putAll(headers);
                Map<String, String> map = requestTemporaryWrapper.getMap();
                if (CollectionUtil.isNotEmpty(map)) {
                    newHeaders.setAll(map);
                }
                if (CollectionUtil.isNotEmpty(headMap)) {
                    newHeaders.setAll(headMap);
                }
                if (contentLength > 0){
                    newHeaders.setContentLength(contentLength);
                }else {
                    newHeaders.set(HttpHeaders.TRANSFER_ENCODING,"chunked");
                }
                if (CollectionUtil.isNotEmpty(headMap) && StringUtil.isNotEmpty(headMap.get(TRACE_ID))) {
                    MDC.put(TRACE_ID,headMap.get(TRACE_ID));
                }
                return newHeaders;
            }

            @Override
            public Flux<DataBuffer> getBody() {
                return outputMessage.getBody();
            }
        };
    }
    /**
     * 指定执行顺序
     * */
    @Override
    public int getOrder() {
        return -2;
    }
    /**
     * 验证是否跳过token验证
     * */
    public boolean skipCheckToken(String url){
        if (gatewayProperty.getCheckTokenPaths() == null) {
            return true;
        }
        for (String skipCheckTokenPath : gatewayProperty.getCheckTokenPaths()) {
            if (PATH_MATCHER.match(skipCheckTokenPath, url)) {
                return false;
            }
        }
        return true;
    }
    /**
     * 验证是否跳过参数验证
     * */
    public boolean skipCheckParameter(String url){
        if (gatewayProperty.getCheckSkipParmeterPaths() == null) {
            return false;
        }
        for (String skipCheckTokenPath : gatewayProperty.getCheckSkipParmeterPaths()) {
            if (PATH_MATCHER.match(skipCheckTokenPath, url)) {
                return true;
            }
        }
        return false;
    }
    /**
     * 验证请求头的参数noVerify = true
     * */
    public boolean checkParameter(String originalBody,String noVerify){
        return (!(VERIFY_VALUE.equals(noVerify))) && StringUtil.isNotEmpty(originalBody);
    }
    /**
     * 验证是否需要userId
     * */
    private boolean checkNeedUserId(String url){
        if (gatewayProperty.getUserIdPaths() == null) {
            return false;
        }
        for (String userIdPath : gatewayProperty.getUserIdPaths()) {
            if (PATH_MATCHER.match(userIdPath, url)) {
                return true;
            }
        }
        return false;
    }
}
