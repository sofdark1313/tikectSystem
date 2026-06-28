<template>
  <div class="app-container">
    <div class="pay-header">
      <button class="back" type="button" @click="goBack"><el-icon><ArrowLeftBold /></el-icon></button>
      <div class="content"><span>确认支付</span></div>
    </div>
    <div class="pay-section">
      <div class="order-summary" v-if="orderDetailData">
        <div class="summary-row">
          <span>订单编号</span>
          <strong>{{ orderNumber }}</strong>
        </div>
        <div class="summary-row">
          <span>订单名称</span>
          <strong>{{ orderDetailData.programTitle }}</strong>
        </div>
        <div class="summary-row amount">
          <span>应付金额</span>
          <strong>￥{{ orderDetailData.orderPrice }}</strong>
        </div>
      </div>
      <el-button type="primary" class="payContinue" :loading="paying" :disabled="paying || !orderDetailData" @click="confirmPay">
        {{ paying ? '支付中' : '确认支付' }}
      </el-button>
    </div>
  </div>
</template>

<script setup name="PayMethod">
import {ref, watch} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {ElMessage} from "element-plus";
import {getOrderDetailApi, orderPayApi, payCheckApi} from "@/api/order.js";
//订单编号
const orderNumber = ref('')
//订单详情数据
const orderDetailData = ref(null);
const paying = ref(false);
const detailRequestId = ref(0);
const route = useRoute();
const router = useRouter();
const PAY_STATUS = 3;
const ORDER_PAY_CODE = 40017;


async function confirmPay() {
  if (paying.value) {
    return;
  }
  paying.value = true;
  try {
    if (!orderDetailData.value) {
      await getOrderDetail();
    }
    if (!orderDetailData.value) {
      throw new Error('订单信息不存在');
    }
    if (orderDetailData.value.orderStatus == PAY_STATUS) {
      goPaySuccess();
      return;
    }
    const orderPayParams = {
      platform: 3,
      orderNumber: orderNumber.value,
      subject: orderDetailData.value.programTitle,
      price: orderDetailData.value.orderPrice,
      channel: 'simple',
      payBillType: 1
    }
    const payResponse = await orderPayApi(orderPayParams);
    if (payResponse.code != '0' && payResponse.code != ORDER_PAY_CODE) {
      throw new Error(payResponse.message || '支付失败，请稍后重试');
    }
    await confirmPayStatus();
    goPaySuccess();
  } catch (error) {
    ElMessage.error(error.message || '支付失败，请稍后重试')
  } finally {
    paying.value = false;
  }
}

function goBack() {
  router.back();
}

async function confirmPayStatus() {
  try {
    const checkResponse = await payCheckApi({orderNumber: orderNumber.value});
    if (checkResponse.code == '0' && checkResponse.data && checkResponse.data.orderStatus == PAY_STATUS) {
      orderDetailData.value.orderStatus = PAY_STATUS;
    }
  } catch (error) {
    console.warn('pay status check failed', error);
  }
}

function goPaySuccess() {
  const orderNumberText = String(orderNumber.value);
  localStorage.setItem('orderNumber', orderNumberText)
  router.replace({
    path: '/order/paySuccess',
    query: {orderNumber: orderNumberText},
    state: {'orderNumber': orderNumberText}
  })
}

//跳转后的接收值
watch(() => route.query.orderNumber, () => {
  getOrderDetail().catch(error => {
    if (error && error.message) {
      ElMessage.error(error.message);
    }
  })
}, {immediate: true})
//订单详情方法
function getOrderDetail() {
  const currentOrderNumber = route.query.orderNumber ? String(route.query.orderNumber) : '';
  orderNumber.value = currentOrderNumber;
  orderDetailData.value = null;
  if (currentOrderNumber == '' || currentOrderNumber == null) {
    router.replace({path:'/orderManagement/index'})
    return Promise.reject(new Error('订单号不存在'));
  }
  const requestId = detailRequestId.value + 1;
  detailRequestId.value = requestId;
  const orderDetailParams = {'orderNumber': currentOrderNumber}
  //传值-订单号
  return getOrderDetailApi(orderDetailParams).then(response => {
    if (detailRequestId.value !== requestId) {
      return;
    }
    if (response.code != '0' || !response.data) {
      throw new Error(response.message || '订单不存在');
    }
    if (String(response.data.orderNumber) !== currentOrderNumber) {
      throw new Error('订单详情与当前订单不一致');
    }
    orderDetailData.value = response.data;
  })
}

</script>

<style scoped lang="scss">
.app-container {
  min-height: 100vh;
  background:
      linear-gradient(135deg, rgba(245, 158, 11, .12), transparent 34%),
      var(--app-bg);
  .pay-header {
    display: flex;
    justify-content: center;
    align-items: center;
    height: 92px;
    padding: 0 55px;
    background-color: #111113;
    position: relative;
    border-bottom: 3px solid var(--app-accent);
    box-shadow: 0 18px 46px rgba(24, 24, 27, .20);

    .back {
      width: 40px;
      height: 40px;
      position: absolute;
      left: 55px;
      border: 0;
      background: transparent;
      padding: 0;
      cursor: pointer;
      color: #fff;
      .el-icon{
        font-size: 40px;
      }
    }

    .content {
      text-align: center;
      span {
        font-size: 34px;
        font-weight: 700;
        color: #333;
        color: #fff;
        height: 70px;
        display: inline-block;
        line-height: 70px;
        width: 200px;
        margin-left: 20px;
      }
    }
  }
  .pay-section{
    width: 720px;
    margin: 120px auto 0;
    .order-summary{
      background: var(--app-surface);
      border: 1px solid var(--app-border);
      border-radius: 8px;
      padding: 30px 36px;
      margin-bottom: 32px;
      box-shadow: 0 22px 58px rgba(24, 24, 27, .14);
      position: relative;
      overflow: hidden;

      &::before {
        content: "";
        position: absolute;
        top: 0;
        left: 0;
        right: 0;
        height: 7px;
        background: linear-gradient(90deg, var(--app-accent), var(--app-danger));
      }
      .summary-row{
        display: flex;
        justify-content: space-between;
        align-items: center;
        min-height: 46px;
        font-size: 18px;
        color: #606266;
        strong{
          max-width: 460px;
          text-align: right;
          color: #303133;
          font-weight: 600;
          word-break: break-all;
        }
      }
      .amount{
        strong{
          color: var(--app-accent);
          color: var(--app-danger);
          font-size: 28px;
        }
      }
    }
    .payContinue{
      width: 100%;
      height: 72px;
      font-size: 26px;
      border-radius: 8px;
      background: var(--app-accent);
      border-color: var(--app-accent);
      color: #111;
      font-weight: 800;
      box-shadow: 0 18px 34px rgba(245, 158, 11, .26);
    }
  }
}

</style>
