package com.tikectsystem.service;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.fastjson.JSON;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tikectsystem.dto.NotifyDto;
import com.tikectsystem.dto.PayBillDto;
import com.tikectsystem.dto.PayDto;
import com.tikectsystem.dto.RefundDto;
import com.tikectsystem.dto.TradeCheckDto;
import com.tikectsystem.entity.PayBill;
import com.tikectsystem.entity.RefundBill;
import com.tikectsystem.enums.BaseCode;
import com.tikectsystem.enums.PayBillStatus;
import com.tikectsystem.exception.TikectsystemFrameException;
import com.tikectsystem.mapper.PayBillMapper;
import com.tikectsystem.mapper.RefundBillMapper;
import com.tikectsystem.servicelock.annotion.ServiceLock;
import com.tikectsystem.util.DateUtils;
import com.tikectsystem.vo.NotifyVo;
import com.tikectsystem.vo.PayBillVo;
import com.tikectsystem.vo.TradeCheckVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;

import static com.tikectsystem.constant.Constant.ALIPAY_NOTIFY_FAILURE_RESULT;
import static com.tikectsystem.constant.Constant.ALIPAY_NOTIFY_SUCCESS_RESULT;
import static com.tikectsystem.core.DistributedLockConstants.COMMON_PAY;
import static com.tikectsystem.core.DistributedLockConstants.TRADE_CHECK;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 支付 service
 * @author: 阿星不是程序员
 **/
@Slf4j
@Service
public class PayService {

    private static final String SIMPLE_PAY_CHANNEL = "simple";

    private static final String SIMPLE_PAY_SCENE = "本地模拟支付";

    private static final String SIMPLE_PAY_RESULT = "PAY_SUCCESS";
    
    @Autowired
    private PayBillMapper payBillMapper;
    
    @Autowired
    private RefundBillMapper refundBillMapper;
    
    @Autowired
    private UidGenerator uidGenerator;
    
    /**
     * 通用支付，用订单号加锁防止多次支付成功。当前演示版不再对接第三方渠道，点击确认即写入支付成功账单。
     * */
    @ServiceLock(name = COMMON_PAY,keys = {"#payDto.orderNumber"})
    @Transactional(rollbackFor = Exception.class)
    public String commonPay(PayDto payDto) {
        LambdaQueryWrapper<PayBill> payBillLambdaQueryWrapper = 
                Wrappers.lambdaQuery(PayBill.class).eq(PayBill::getOutOrderNo, payDto.getOrderNumber());
        PayBill payBill = payBillMapper.selectOne(payBillLambdaQueryWrapper);
        if (Objects.nonNull(payBill) && Objects.equals(payBill.getPayBillStatus(), PayBillStatus.PAY.getCode())) {
            return SIMPLE_PAY_RESULT;
        }
        if (Objects.nonNull(payBill) && !Objects.equals(payBill.getPayBillStatus(), PayBillStatus.NO_PAY.getCode())) {
            throw new TikectsystemFrameException(BaseCode.PAY_BILL_IS_NOT_NO_PAY);
        }

        PayBill savePayBill = new PayBill();
        savePayBill.setOutOrderNo(String.valueOf(payDto.getOrderNumber()));
        savePayBill.setPayNumber(String.valueOf(uidGenerator.getUid()));
        savePayBill.setTradeNumber(SIMPLE_PAY_CHANNEL + "-" + payDto.getOrderNumber());
        savePayBill.setPayChannel(SIMPLE_PAY_CHANNEL);
        savePayBill.setPayScene(SIMPLE_PAY_SCENE);
        savePayBill.setSubject(payDto.getSubject());
        savePayBill.setPayAmount(payDto.getPrice());
        savePayBill.setPayBillType(payDto.getPayBillType());
        savePayBill.setPayBillStatus(PayBillStatus.PAY.getCode());
        savePayBill.setPayTime(DateUtils.now());
        if (payBill == null) {
            savePayBill.setId(uidGenerator.getUid());
            payBillMapper.insert(savePayBill);
        } else {
            LambdaUpdateWrapper<PayBill> updateWrapper = Wrappers.lambdaUpdate(PayBill.class)
                    .eq(PayBill::getId, payBill.getId())
                    .eq(PayBill::getOutOrderNo, savePayBill.getOutOrderNo())
                    .set(PayBill::getPayNumber, savePayBill.getPayNumber())
                    .set(PayBill::getTradeNumber, savePayBill.getTradeNumber())
                    .set(PayBill::getPayChannel, savePayBill.getPayChannel())
                    .set(PayBill::getPayScene, savePayBill.getPayScene())
                    .set(PayBill::getSubject, savePayBill.getSubject())
                    .set(PayBill::getPayAmount, savePayBill.getPayAmount())
                    .set(PayBill::getPayBillType, savePayBill.getPayBillType())
                    .set(PayBill::getPayBillStatus, savePayBill.getPayBillStatus())
                    .set(PayBill::getPayTime, savePayBill.getPayTime());
            payBillMapper.update(null, updateWrapper);
        }

        return SIMPLE_PAY_RESULT;
    }

    @Transactional(rollbackFor = Exception.class)
    public NotifyVo notify(NotifyDto notifyDto){
        NotifyVo notifyVo = new NotifyVo();

        log.info("回调通知参数 ===> {}", JSON.toJSONString(notifyDto));
        Map<String, String> params = notifyDto.getParams();
        if (params == null || params.get("out_trade_no") == null) {
            notifyVo.setPayResult(ALIPAY_NOTIFY_FAILURE_RESULT);
            return notifyVo;
        }
        LambdaQueryWrapper<PayBill> payBillLambdaQueryWrapper =
                Wrappers.lambdaQuery(PayBill.class).eq(PayBill::getOutOrderNo, params.get("out_trade_no"));
        //查询账单
        PayBill payBill = payBillMapper.selectOne(payBillLambdaQueryWrapper);
        //查询账单是否存在
        if (Objects.isNull(payBill)) {
            log.error("账单为空 notifyDto : {}",JSON.toJSONString(notifyDto));
            notifyVo.setPayResult(ALIPAY_NOTIFY_FAILURE_RESULT);
            return notifyVo;
        }
        //如果账单已支付了，直接返回成功状态
        if (Objects.equals(payBill.getPayBillStatus(), PayBillStatus.PAY.getCode())) {
            log.info("账单已支付 notifyDto : {}",JSON.toJSONString(notifyDto));
            notifyVo.setOutTradeNo(payBill.getOutOrderNo());
            notifyVo.setPayResult(ALIPAY_NOTIFY_SUCCESS_RESULT);
            return notifyVo;
        }
        //如果账单已取消了，直接返回成功状态
        if (Objects.equals(payBill.getPayBillStatus(), PayBillStatus.CANCEL.getCode())) {
            log.info("账单已取消 notifyDto : {}",JSON.toJSONString(notifyDto));
            notifyVo.setOutTradeNo(payBill.getOutOrderNo());
            notifyVo.setPayResult(ALIPAY_NOTIFY_SUCCESS_RESULT);
            return notifyVo;
        }
        //如果账单已退单了，直接返回成功状态
        if (Objects.equals(payBill.getPayBillStatus(), PayBillStatus.REFUND.getCode())) {
            log.info("账单已退单 notifyDto : {}",JSON.toJSONString(notifyDto));
            notifyVo.setOutTradeNo(payBill.getOutOrderNo());
            notifyVo.setPayResult(ALIPAY_NOTIFY_SUCCESS_RESULT);
            return notifyVo;
        }
        //更新账单为支付状态
        PayBill updatePayBill = new PayBill();
        updatePayBill.setPayBillStatus(PayBillStatus.PAY.getCode());
        LambdaUpdateWrapper<PayBill> payBillLambdaUpdateWrapper =
                Wrappers.lambdaUpdate(PayBill.class).eq(PayBill::getOutOrderNo, params.get("out_trade_no"));
        payBillMapper.update(updatePayBill,payBillLambdaUpdateWrapper);
        notifyVo.setOutTradeNo(payBill.getOutOrderNo());
        notifyVo.setPayResult(ALIPAY_NOTIFY_SUCCESS_RESULT);
        return notifyVo;
    }

    @Transactional(rollbackFor = Exception.class)
    @ServiceLock(name = TRADE_CHECK,keys = {"#tradeCheckDto.outTradeNo"})
    public TradeCheckVo tradeCheck(TradeCheckDto tradeCheckDto) {
        TradeCheckVo tradeCheckVo = new TradeCheckVo();
        LambdaQueryWrapper<PayBill> payBillLambdaQueryWrapper =
                Wrappers.lambdaQuery(PayBill.class).eq(PayBill::getOutOrderNo, tradeCheckDto.getOutTradeNo());
        PayBill payBill = payBillMapper.selectOne(payBillLambdaQueryWrapper);
        if (Objects.isNull(payBill)) {
            log.error("账单为空 tradeCheckDto : {}",JSON.toJSONString(tradeCheckDto));
            return tradeCheckVo;
        }
        tradeCheckVo.setSuccess(true);
        tradeCheckVo.setOutTradeNo(payBill.getOutOrderNo());
        tradeCheckVo.setTotalAmount(payBill.getPayAmount());
        tradeCheckVo.setPayBillStatus(payBill.getPayBillStatus());
        return tradeCheckVo;
    }
    
    public String refund(RefundDto refundDto) {
        PayBill payBill = payBillMapper.selectOne(Wrappers.lambdaQuery(PayBill.class)
                .eq(PayBill::getOutOrderNo, refundDto.getOrderNumber()));
        if (Objects.isNull(payBill)) {
            throw new TikectsystemFrameException(BaseCode.PAY_BILL_NOT_EXIST);
        }
        
        if (!Objects.equals(payBill.getPayBillStatus(), PayBillStatus.PAY.getCode())) {
            throw new TikectsystemFrameException(BaseCode.PAY_BILL_IS_NOT_PAY_STATUS);
        }
        
        if (refundDto.getAmount().compareTo(payBill.getPayAmount()) > 0) {
            throw new TikectsystemFrameException(BaseCode.REFUND_AMOUNT_GREATER_THAN_PAY_AMOUNT);
        }
        
        RefundBill refundBill = new RefundBill();
        refundBill.setId(uidGenerator.getUid());
        refundBill.setOutOrderNo(payBill.getOutOrderNo());
        refundBill.setPayBillId(payBill.getId());
        refundBill.setRefundAmount(refundDto.getAmount());
        refundBill.setRefundStatus(2);
        refundBill.setRefundTime(DateUtils.now());
        refundBill.setReason(refundDto.getReason());
        refundBillMapper.insert(refundBill);

        PayBill updatePayBill = new PayBill();
        updatePayBill.setId(payBill.getId());
        updatePayBill.setPayBillStatus(PayBillStatus.REFUND.getCode());
        payBillMapper.updateById(updatePayBill);
        return refundBill.getOutOrderNo();
    }
    
    public PayBillVo detail(PayBillDto payBillDto) {
        PayBillVo payBillVo = new PayBillVo();
        LambdaQueryWrapper<PayBill> payBillLambdaQueryWrapper =
                Wrappers.lambdaQuery(PayBill.class).eq(PayBill::getOutOrderNo, payBillDto.getOrderNumber());
        PayBill payBill = payBillMapper.selectOne(payBillLambdaQueryWrapper);
        if (Objects.nonNull(payBill)) {
            BeanUtil.copyProperties(payBill,payBillVo);
        }
        return payBillVo;
    }
}
