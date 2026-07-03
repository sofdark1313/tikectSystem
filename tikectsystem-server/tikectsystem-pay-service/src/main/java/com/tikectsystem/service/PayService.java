package com.tikectsystem.service;

import cn.hutool.core.bean.BeanUtil;
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

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

import static com.tikectsystem.constant.Constant.ALIPAY_NOTIFY_FAILURE_RESULT;
import static com.tikectsystem.constant.Constant.ALIPAY_NOTIFY_SUCCESS_RESULT;
import static com.tikectsystem.core.DistributedLockConstants.COMMON_PAY;
import static com.tikectsystem.core.DistributedLockConstants.TRADE_CHECK;

/**
 * 支付账单服务。
 */
@Slf4j
@Service
public class PayService {

    private static final String SIMPLE_PAY_CHANNEL = "simple";

    private static final String SIMPLE_PAY_SCENE = "本地模拟支付";

    private static final String SIMPLE_PAY_RESULT = "PAY_SUCCESS";

    private static final int REFUND_SUCCESS_STATUS = 2;

    @Autowired
    private PayBillMapper payBillMapper;

    @Autowired
    private RefundBillMapper refundBillMapper;

    @Autowired
    private UidGenerator uidGenerator;

    /**
     * 通用支付。当前演示版本使用本地模拟支付，必须保证同一订单号只从未支付态流转到已支付态。
     */
    @ServiceLock(name = COMMON_PAY, keys = {"#payDto.orderNumber"})
    @Transactional(rollbackFor = Exception.class)
    public String commonPay(PayDto payDto) {
        if (payDto.getPrice() == null || payDto.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new TikectsystemFrameException(BaseCode.PAY_AMOUNT_MUST_BE_POSITIVE);
        }

        PayBill payBill = payBillMapper.selectOne(Wrappers.lambdaQuery(PayBill.class)
                .eq(PayBill::getOutOrderNo, payDto.getOrderNumber()));
        if (Objects.nonNull(payBill) && Objects.equals(payBill.getPayBillStatus(), PayBillStatus.PAY.getCode())) {
            return SIMPLE_PAY_RESULT;
        }
        if (Objects.nonNull(payBill) && !Objects.equals(payBill.getPayBillStatus(), PayBillStatus.NO_PAY.getCode())) {
            throw new TikectsystemFrameException(BaseCode.PAY_BILL_IS_NOT_NO_PAY);
        }

        PayBill savePayBill = buildSimplePayBill(payDto);
        if (payBill == null) {
            savePayBill.setId(uidGenerator.getUid());
            if (payBillMapper.insert(savePayBill) != 1) {
                throw new TikectsystemFrameException(BaseCode.PAY_BILL_UPDATE_ERROR);
            }
        } else {
            LambdaUpdateWrapper<PayBill> updateWrapper = Wrappers.lambdaUpdate(PayBill.class)
                    .eq(PayBill::getId, payBill.getId())
                    .eq(PayBill::getOutOrderNo, savePayBill.getOutOrderNo())
                    .eq(PayBill::getPayBillStatus, PayBillStatus.NO_PAY.getCode())
                    .set(PayBill::getPayNumber, savePayBill.getPayNumber())
                    .set(PayBill::getTradeNumber, savePayBill.getTradeNumber())
                    .set(PayBill::getPayChannel, savePayBill.getPayChannel())
                    .set(PayBill::getPayScene, savePayBill.getPayScene())
                    .set(PayBill::getSubject, savePayBill.getSubject())
                    .set(PayBill::getPayAmount, savePayBill.getPayAmount())
                    .set(PayBill::getPayBillType, savePayBill.getPayBillType())
                    .set(PayBill::getPayBillStatus, savePayBill.getPayBillStatus())
                    .set(PayBill::getPayTime, savePayBill.getPayTime());
            if (payBillMapper.update(null, updateWrapper) != 1) {
                throw new TikectsystemFrameException(BaseCode.PAY_BILL_IS_NOT_NO_PAY);
            }
        }

        return SIMPLE_PAY_RESULT;
    }

    /**
     * 支付回调处理。日志只记录可审计摘要，避免输出签名串和完整回调参数。
     */
    @Transactional(rollbackFor = Exception.class)
    public NotifyVo notify(NotifyDto notifyDto) {
        Map<String, String> params = notifyDto.getParams();
        String outTradeNo = getNotifyParam(params, "out_trade_no");
        log.info("pay notify received, channel:{}, outTradeNo:{}, tradeStatus:{}, totalAmount:{}",
                notifyDto.getChannel(), outTradeNo, getNotifyParam(params, "trade_status"),
                getNotifyParam(params, "total_amount"));
        if (!Objects.equals(SIMPLE_PAY_CHANNEL, notifyDto.getChannel())) {
            log.warn("pay notify rejected by unsupported channel, channel:{}, outTradeNo:{}",
                    notifyDto.getChannel(), outTradeNo);
            return buildNotifyVo(null, ALIPAY_NOTIFY_FAILURE_RESULT);
        }
        if (outTradeNo == null) {
            return buildNotifyVo(null, ALIPAY_NOTIFY_FAILURE_RESULT);
        }

        PayBill payBill = payBillMapper.selectOne(Wrappers.lambdaQuery(PayBill.class)
                .eq(PayBill::getOutOrderNo, outTradeNo));
        if (Objects.isNull(payBill)) {
            log.warn("pay notify bill not exist, channel:{}, outTradeNo:{}", notifyDto.getChannel(), outTradeNo);
            return buildNotifyVo(null, ALIPAY_NOTIFY_FAILURE_RESULT);
        }
        if (isTerminalPayBillStatus(payBill.getPayBillStatus())) {
            log.info("pay notify ignored by terminal status, outTradeNo:{}, status:{}",
                    payBill.getOutOrderNo(), payBill.getPayBillStatus());
            return buildNotifyVo(payBill.getOutOrderNo(), ALIPAY_NOTIFY_SUCCESS_RESULT);
        }
        if (!Objects.equals(payBill.getPayBillStatus(), PayBillStatus.NO_PAY.getCode())) {
            log.warn("pay notify status not allowed, outTradeNo:{}, status:{}",
                    payBill.getOutOrderNo(), payBill.getPayBillStatus());
            return buildNotifyVo(null, ALIPAY_NOTIFY_FAILURE_RESULT);
        }

        PayBill updatePayBill = new PayBill();
        updatePayBill.setPayBillStatus(PayBillStatus.PAY.getCode());
        updatePayBill.setPayTime(DateUtils.now());
        updatePayBill.setTradeNumber(getNotifyParam(params, "trade_no"));
        updatePayBill.setPayChannel(notifyDto.getChannel());
        LambdaUpdateWrapper<PayBill> payBillLambdaUpdateWrapper = Wrappers.lambdaUpdate(PayBill.class)
                .eq(PayBill::getOutOrderNo, outTradeNo)
                .eq(PayBill::getPayBillStatus, PayBillStatus.NO_PAY.getCode());
        if (payBillMapper.update(updatePayBill, payBillLambdaUpdateWrapper) != 1) {
            log.warn("pay notify update bill failed, outTradeNo:{}", outTradeNo);
            return buildNotifyVo(null, ALIPAY_NOTIFY_FAILURE_RESULT);
        }
        return buildNotifyVo(payBill.getOutOrderNo(), ALIPAY_NOTIFY_SUCCESS_RESULT);
    }

    /**
     * 查询本地支付账单状态。
     */
    @Transactional(rollbackFor = Exception.class)
    @ServiceLock(name = TRADE_CHECK, keys = {"#tradeCheckDto.outTradeNo"})
    public TradeCheckVo tradeCheck(TradeCheckDto tradeCheckDto) {
        TradeCheckVo tradeCheckVo = new TradeCheckVo();
        LambdaQueryWrapper<PayBill> payBillLambdaQueryWrapper =
                Wrappers.lambdaQuery(PayBill.class).eq(PayBill::getOutOrderNo, tradeCheckDto.getOutTradeNo());
        PayBill payBill = payBillMapper.selectOne(payBillLambdaQueryWrapper);
        if (Objects.isNull(payBill)) {
            log.warn("pay bill not exist, outTradeNo:{}, channel:{}",
                    tradeCheckDto.getOutTradeNo(), tradeCheckDto.getChannel());
            return tradeCheckVo;
        }
        tradeCheckVo.setSuccess(true);
        tradeCheckVo.setOutTradeNo(payBill.getOutOrderNo());
        tradeCheckVo.setTotalAmount(payBill.getPayAmount());
        tradeCheckVo.setPayBillStatus(payBill.getPayBillStatus());
        return tradeCheckVo;
    }

    /**
     * 本地退款。与支付使用同一订单号锁，避免支付和退款并发修改同一账单。
     */
    @ServiceLock(name = COMMON_PAY, keys = {"#refundDto.orderNumber"})
    @Transactional(rollbackFor = Exception.class)
    public String refund(RefundDto refundDto) {
        if (refundDto.getAmount() == null || refundDto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new TikectsystemFrameException(BaseCode.REFUND_AMOUNT_MUST_BE_POSITIVE);
        }

        PayBill payBill = payBillMapper.selectOne(Wrappers.lambdaQuery(PayBill.class)
                .eq(PayBill::getOutOrderNo, refundDto.getOrderNumber()));
        if (Objects.isNull(payBill)) {
            throw new TikectsystemFrameException(BaseCode.PAY_BILL_NOT_EXIST);
        }
        if (payBill.getPayAmount() == null) {
            throw new TikectsystemFrameException(BaseCode.PAY_BILL_UPDATE_ERROR);
        }
        if (refundDto.getAmount().compareTo(payBill.getPayAmount()) > 0) {
            throw new TikectsystemFrameException(BaseCode.REFUND_AMOUNT_GREATER_THAN_PAY_AMOUNT);
        }

        RefundBill oldRefundBill = selectRefundBill(payBill.getOutOrderNo());
        if (Objects.equals(payBill.getPayBillStatus(), PayBillStatus.REFUND.getCode())) {
            assertRefundAmountMatches(oldRefundBill, refundDto);
            return payBill.getOutOrderNo();
        }
        if (!Objects.equals(payBill.getPayBillStatus(), PayBillStatus.PAY.getCode())) {
            throw new TikectsystemFrameException(BaseCode.PAY_BILL_IS_NOT_PAY_STATUS);
        }
        if (oldRefundBill != null) {
            assertRefundAmountMatches(oldRefundBill, refundDto);
            markPayBillRefunded(payBill);
            return payBill.getOutOrderNo();
        }

        markPayBillRefunded(payBill);
        RefundBill refundBill = new RefundBill();
        refundBill.setId(uidGenerator.getUid());
        refundBill.setOutOrderNo(payBill.getOutOrderNo());
        refundBill.setPayBillId(payBill.getId());
        refundBill.setRefundAmount(refundDto.getAmount());
        refundBill.setRefundStatus(REFUND_SUCCESS_STATUS);
        refundBill.setRefundTime(DateUtils.now());
        refundBill.setReason(refundDto.getReason());
        if (refundBillMapper.insert(refundBill) != 1) {
            throw new TikectsystemFrameException(BaseCode.REFUND_ERROR);
        }
        return refundBill.getOutOrderNo();
    }

    /**
     * 查询支付账单详情。
     */
    public PayBillVo detail(PayBillDto payBillDto) {
        PayBillVo payBillVo = new PayBillVo();
        LambdaQueryWrapper<PayBill> payBillLambdaQueryWrapper =
                Wrappers.lambdaQuery(PayBill.class).eq(PayBill::getOutOrderNo, payBillDto.getOrderNumber());
        PayBill payBill = payBillMapper.selectOne(payBillLambdaQueryWrapper);
        if (Objects.nonNull(payBill)) {
            BeanUtil.copyProperties(payBill, payBillVo);
        }
        return payBillVo;
    }

    private PayBill buildSimplePayBill(PayDto payDto) {
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
        return savePayBill;
    }

    private String getNotifyParam(Map<String, String> params, String key) {
        if (params == null) {
            return null;
        }
        return params.get(key);
    }

    private NotifyVo buildNotifyVo(String outTradeNo, String payResult) {
        NotifyVo notifyVo = new NotifyVo();
        notifyVo.setOutTradeNo(outTradeNo);
        notifyVo.setPayResult(payResult);
        return notifyVo;
    }

    private boolean isTerminalPayBillStatus(Integer payBillStatus) {
        return Objects.equals(payBillStatus, PayBillStatus.PAY.getCode()) ||
                Objects.equals(payBillStatus, PayBillStatus.CANCEL.getCode()) ||
                Objects.equals(payBillStatus, PayBillStatus.REFUND.getCode());
    }

    private RefundBill selectRefundBill(String outOrderNo) {
        return refundBillMapper.selectOne(Wrappers.lambdaQuery(RefundBill.class)
                .eq(RefundBill::getOutOrderNo, outOrderNo)
                .last("limit 1"));
    }

    private void assertRefundAmountMatches(RefundBill refundBill, RefundDto refundDto) {
        if (refundBill == null) {
            return;
        }
        if (refundBill.getRefundAmount() == null ||
                refundBill.getRefundAmount().compareTo(refundDto.getAmount()) != 0) {
            throw new TikectsystemFrameException(BaseCode.REFUND_BILL_AMOUNT_NOT_MATCH);
        }
    }

    private void markPayBillRefunded(PayBill payBill) {
        PayBill updatePayBill = new PayBill();
        updatePayBill.setPayBillStatus(PayBillStatus.REFUND.getCode());
        int updateCount = payBillMapper.update(updatePayBill, Wrappers.lambdaUpdate(PayBill.class)
                .eq(PayBill::getId, payBill.getId())
                .eq(PayBill::getPayBillStatus, PayBillStatus.PAY.getCode()));
        if (updateCount != 1) {
            throw new TikectsystemFrameException(BaseCode.PAY_BILL_IS_NOT_PAY_STATUS);
        }
    }
}
