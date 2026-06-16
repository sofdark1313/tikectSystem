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
import {ref,onMounted} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {ElMessage} from "element-plus";
import {getOrderDetailApi, orderPayApi, payCheckApi} from "@/api/order.js";
//订单编号
const orderNumber = ref('')
//订单详情数据
const orderDetailData = ref(null);
const paying = ref(false);
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
    const checkResponse = await payCheckApi({orderNumber: orderNumber.value});
    if (checkResponse.code != '0') {
      throw new Error(checkResponse.message || '支付状态确认失败');
    }
    if (!checkResponse.data || checkResponse.data.orderStatus != PAY_STATUS) {
      throw new Error('支付状态尚未确认，请稍后在订单列表查看');
    }
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
onMounted(() => {
  getOrderDetail().catch(() => {})
})
//订单详情方法
function getOrderDetail() {
  orderNumber.value = route.query.orderNumber || history.state.orderNumber || localStorage.getItem('orderNumber');
  if (orderNumber.value == '' || orderNumber.value == null) {
    router.replace({path:'/orderManagement/index'})
    return Promise.reject(new Error('订单号不存在'));
  }
  const orderDetailParams = {'orderNumber': orderNumber.value}
  //传值-订单号
  localStorage.setItem('orderNumber',orderNumber.value)
  return getOrderDetailApi(orderDetailParams).then(response => {
    if (response.code != '0' || !response.data) {
      throw new Error(response.message || '订单不存在');
    }
    orderDetailData.value = response.data;
  })
}

</script>

<style scoped lang="scss">
.app-container {
  min-height: 100vh;
  background: #f6f7fb;
  .pay-header {
    display: flex;
    justify-content: center;
    align-items: center;
    height: 92px;
    padding: 0 55px;
    background-color: #fff;
    position: relative;

    .back {
      width: 40px;
      height: 40px;
      position: absolute;
      left: 55px;
      border: 0;
      background: transparent;
      padding: 0;
      cursor: pointer;
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
      background: #fff;
      border: 1px solid #ebeef5;
      border-radius: 8px;
      padding: 28px 34px;
      margin-bottom: 32px;
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
          color: rgb(255, 40, 105);
          font-size: 28px;
        }
      }
    }
    .payContinue{
      width: 100%;
      height: 72px;
      font-size: 26px;
    }
  }
}

</style>
