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
      <el-button type="primary" class="payContinue" :loading="paying" @click="confirmPay">确认支付</el-button>
    </div>
  </div>
</template>

<script setup name="PayMethod">
import {ref,onMounted} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {ElMessage} from "element-plus";
import {getOrderDetailApi,orderPayApi} from "@/api/order.js";
//订单编号
const orderNumber = ref('')
//订单详情数据
const orderDetailData = ref(null);
const paying = ref(false);
const route = useRoute();
const router = useRouter();


function confirmPay() {
  if (paying.value) {
    return;
  }
  const payAction = orderDetailData.value ? Promise.resolve() : getOrderDetail();
  paying.value = true;
  payAction.then(() => {
    const orderPayParams = {
      platform: 3,
      orderNumber: orderNumber.value,
      subject: orderDetailData.value.programTitle,
      price: orderDetailData.value.orderPrice,
      channel: 'simple',
      payBillType: 1
    }
    return orderPayApi(orderPayParams);
  }).then(() => {
    localStorage.setItem('orderNumber', orderNumber.value)
    router.replace({path:'/order/paySuccess'})
  }).catch(() => {
    ElMessage.error('支付失败，请稍后重试')
  }).finally(() => {
    paying.value = false;
  })
}

function goBack() {
  router.back();
}

//跳转后的接收值
onMounted(() => {
  getOrderDetail().catch(() => {})
})
//订单详情方法
function getOrderDetail() {
  orderNumber.value = history.state.orderNumber || route.query.orderNumber || localStorage.getItem('orderNumber');
  if (orderNumber.value == '' || orderNumber.value == null) {
    router.replace({path:'/orderManagement/index'})
    return Promise.reject();
  }
  const orderDetailParams = {'orderNumber': orderNumber.value}
  //传值-订单号
  localStorage.setItem('orderNumber',orderNumber.value)
  return getOrderDetailApi(orderDetailParams).then(response => {
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
