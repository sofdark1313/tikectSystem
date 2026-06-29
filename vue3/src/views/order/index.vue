<template>
  <div class="app-container">
    <div class="order-checkout">
      <section class="checkout-hero">
        <div class="hero-kicker">订单确认</div>
        <h1>{{ detailList.title }}</h1>
        <div class="hero-place">{{ detailList.areaName }} | {{ detailList.place }}</div>
        <div class="hero-divider"></div>
        <div class="hero-meta">
          <div>
            <span class="meta-label">场次</span>
            <strong>{{ formatDateWithWeekday(detailList.showTime, detailList.showWeekTime) }}</strong>
          </div>
          <div>
            <span class="meta-label">票档</span>
            <strong>￥<span v-if="allPrice == ''">{{ countPrice }}</span><span v-else>{{ allPrice }}</span> × <span v-if="allPrice == ''">1</span><span v-else>{{ num }}</span>张</strong>
          </div>
        </div>
        <p>按付款顺序配票，优先连座配票</p>
      </section>

      <section class="checkout-card service-card">
        <div class="section-title">服务</div>
        <div class="service-tags">
          <span class="service-tag warn" v-if="detailList.permitRefund=='0'">不支持退</span>
          <span class="service-tag ok" v-if="detailList.permitRefund=='1'">条件退</span>
          <span class="service-tag ok" v-if="detailList.permitRefund=='2'">全部退</span>
          <span class="service-tag warn" v-if="detailList.relNameTicketEntrance=='0'">不实名购票和入场</span>
          <span class="service-tag ok" v-if="detailList.relNameTicketEntrance=='1'">实名购票和入场</span>
          <span class="service-tag warn" v-if="detailList.permitChooseSeat=='0'">不支持选座</span>
          <span class="service-tag ok" v-if="detailList.permitChooseSeat=='1'">支持选座</span>
          <span class="service-tag ok" v-if="detailList.electronicDeliveryTicket=='1'">电子票</span>
          <span class="service-tag ok" v-if="detailList.electronicDeliveryTicket=='2'">快递票</span>
          <span class="service-tag ok" v-if="detailList.electronicInvoice=='1'">电子发票</span>
        </div>
      </section>

      <section class="checkout-card audience-card-wrap">
        <div class="section-heading">
          <div>
            <h2>实名观演人</h2>
            <p>仅需选择一位，入场时需携带对应证件</p>
          </div>
          <el-button class="add-user-btn" type="primary" @click="buyTicketInfo">新增</el-button>
        </div>
        <div class="audience-list" v-if="ticketInfoArr && ticketInfoArr.length">
          <div
              class="audience-card"
              :class="{ selected: ticketUserIdArr.includes(item.id) }"
              v-for="item in ticketInfoArr"
              :key="item.id"
          >
            <div class="audience-avatar">{{ item.relName ? item.relName.slice(0, 1) : '*' }}</div>
            <div class="audience-info" v-if="isSHowInfo">
              <strong>{{ item.relName }}</strong>
              <div class="audience-id">
                <span v-if="item.idType == 1">身份证</span>
                <span v-if="item.idType == 2">港澳台居民居住证</span>
                <span v-if="item.idType == 3">港澳居民来往内地通行证</span>
                <span v-if="item.idType == 4">台湾居民来往内地通行证</span>
                <span v-if="item.idType == 5">护照</span>
                <span v-if="item.idType == 6">外国人永久居住证</span>
                <em>{{ item.idNumber }}</em>
              </div>
            </div>
            <el-checkbox class="audience-check" :value="item.id" size="large" @change="getSelectTicketUser(item.id, $event)"></el-checkbox>
          </div>
        </div>
        <div class="audience-empty" v-else>
          暂无实名观演人，请先新增后再提交订单
        </div>
      </section>

      <section class="info-grid">
        <div class="checkout-card info-card">
          <h2>配送方式</h2>
          <strong v-if="detailList.electronicDeliveryTicket=='1'">电子票 <span>直接入场</span></strong>
          <p v-if="detailList.electronicDeliveryTicket=='1'">支付成功后，无需取票，前往票夹查看入场凭证。</p>
        </div>
        <div class="checkout-card info-card">
          <h2>联系方式</h2>
          <strong>{{ telNum }}</strong>
          <p>订单状态和票务通知会发送至该联系方式。</p>
        </div>
        <div class="checkout-card info-card">
          <h2>付款确认</h2>
          <strong>简化支付</strong>
          <p>提交订单后进入确认支付页面，点击按钮即可完成付款。</p>
        </div>
      </section>
    </div>

    <div class="checkout-bar">
      <div class="checkout-note">由于票品为价票券，非普通商品，其背后承载的文化服务具有时效性、稀缺性等特征，一旦订购成功，不支持退换。</div>
      <div class="checkout-total">
        <span class="total-price" v-if="allPrice == ''">￥{{ countPrice }}</span>
        <span class="total-price" v-else>￥{{ allPrice }}</span>
        <span class="total-detail">明细</span>
        <el-button type="primary" class="checkout-submit" :loading="submitLoading" :disabled="submitLoading" @click="submitOrder">
          {{ submitLoading ? '提交中' : '提交订单' }}
        </el-button>
      </div>
    </div>
    <el-dialog
        v-model="dialogVisible"
       style="width: 450px;height:500px;background: #FFE7BA;"

    >
      <div class="content">{{ dialogMessage }}</div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false" class="btn1">返回</el-button>
          <el-button   class="submit btn2"    @click="dialogVisible = false">
            继续尝试
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="orderIndex">
import {ref, nextTick, onActivated, onMounted,onBeforeUnmount } from 'vue'
import {getCurrentDateTime,formatDateWithWeekday,useMitt} from '@/utils/index'
import {useRoute, useRouter} from 'vue-router'
import { getUserIdKey} from "@/utils/auth";
import { getPersonInfoId} from '@/api/personInfo'
import {getTicketUser} from "@/api/buyTicketUser";
import {
  getOrderCacheApi,
  getOrderRequestResultApi,
  orderCreateV1Api,
  orderCreateV2Api,
  orderCreateV3Api,
  orderCreateV4Api
} from '@/api/order.js'
import {ElMessage} from "element-plus";
//获取用户信息
import useUserStore from "../../store/modules/user";

const useUser = useUserStore()
const router = useRouter();
const detailList = ref([])
const allPrice = ref('')
const countPrice = ref('')
const num = ref('')
const telNum = ref('')
const ticketInfoArr = ref([])
const dialogVisible = ref(false)
const dialogMessage = ref('当前排队人数太多，请稍候再试~')
const isSHowInfo = ref(true)//此处设置是为了解决弹出框显示后此处界面也会显示到上层的问题。注意：关闭弹框的时候这里要设置为true
const ticketUserIdArr = ref([])
//票档id
const ticketCategoryId = ref('')
const loading = ref(false)
const submitLoading = ref(false)
const svg = `
        <path class="path" d="
          M 30 15
          L 28 17
          M 25.61 25.61
          A 15 15, 0, 0, 1, 15 30
          A 15 15, 0, 1, 1, 27.99 7.5
          L 15 15
        " style="stroke-width: 4px; fill: rgba(0, 0, 0, 0)"/>`
const pollingTimer = ref(null);
const pollingSessionId = ref(0);
const currentOrderRequestId = ref('');
// V4 异步建单最长等待时间（毫秒）
const orderCreateWaitMillis = 30000;
const ORDER_REQUEST_CREATED = 'ORDER_CREATED';
const ORDER_REQUEST_FAILED_STATUS_SET = ['FAILED', 'CANCELLED', 'EXPIRED'];

//跳转后的接收值
onMounted(()=>{
  currentOrderRequestId.value = ''
  detailList.value  = JSON.parse(history.state.detailList)
  allPrice.value  = history.state.allPrice
  countPrice.value  =history.state.countPrice
  num.value  =history.state.num
  ticketCategoryId.value = history.state.ticketCategoryId
})

getPersonInfoIdList()
getTicketUserList()

async function getPersonInfoIdList() {
  const id = getUserIdKey()
  getPersonInfoId({id: id}).then(response => {
    let {mobile } = response.data
    telNum.value = mobile
  })
}
async function getTicketUserList() {
  const id = getUserIdKey()
  getTicketUser({userId:id}).then(response=>{
    ticketInfoArr.value =response.data
  })
}


function buyTicketInfo(){
  router.replace({path:'/order/buyTicketUser'})

}

function getSelectTicketUser(ticketUserId,isChecked){
  currentOrderRequestId.value = '';
  if (isChecked) {
    ticketUserIdArr.value.push(ticketUserId);
  } else {
    ticketUserIdArr.value = ticketUserIdArr.value.filter((item) => item !== ticketUserId);
  }
}


function getOrderCache(orderNumber){
  const orderNumberParams = {orderNumber}
  return getOrderCacheApi(orderNumberParams).then(response => {
    if (response.code == '0' && response.data != null){
      return String(response.data);
    }
    return '';
  })
}

function ensureOrderRequestId() {
  if (currentOrderRequestId.value !== '') {
    return currentOrderRequestId.value;
  }
  currentOrderRequestId.value = [
    useUser.userId,
    detailList.value.id,
    Date.now(),
    Math.random().toString(36).slice(2, 10)
  ].join('-');
  return currentOrderRequestId.value;
}

function getOrderRequestResult(orderNumber) {
  return getOrderRequestResultApi({orderNumber}).then(response => {
    if (response.code == '0') {
      return response.data || null;
    }
    return null;
  })
}

function goPay(orderNumber) {
  const orderNumberText = String(orderNumber);
  localStorage.setItem('orderNumber', orderNumberText);
  router.replace({
    path: '/order/payMethod',
    query: {orderNumber: orderNumberText},
    state: {'orderNumber': orderNumberText}
  })
}

function submitOrderError() {
  loadingClose();
  submitLoading.value = false;
  ElMessage.error('提交订单失败，请稍后重试');
}

function getResponseMessage(response, defaultMessage = '提交订单失败，请稍后重试') {
  return response && response.message ? response.message : defaultMessage;
}

function getOrderNumberFromResponse(response) {
  if (!response || response.data == null) {
    return null;
  }
  if (typeof response.data === 'object') {
    return response.data.orderNumber || null;
  }
  return response.data;
}

function showOrderCreateFailure(response) {
  loadingClose();
  submitLoading.value = false;
  const code = Number(response && response.code);
  const message = getResponseMessage(response);
  if ([10056, 50012, 50013].includes(code)) {
    dialogShow(message);
    return;
  }
  ElMessage.error(message);
}

//订单查询轮训
const startPolling = (orderNumber,startTime) => {
  stopPolling();
  const currentOrderNumber = String(orderNumber);
  const sessionId = pollingSessionId.value + 1;
  pollingSessionId.value = sessionId;
  let requesting = false;
  pollingTimer.value = setInterval(async () => {
    if (pollingSessionId.value !== sessionId || requesting) {
      return;
    }
    const currentTime = Date.now();
    if (currentTime - startTime >= orderCreateWaitMillis) {
      stopPolling();
      //1. 超过等待时间仍未建单，显示排队弹框
      //2. loading弹出框关闭
      loadingClose();
      //3. 排队弹框显示
      dialogShow('订单创建处理中，请稍后查看订单列表或重试');
      return;
    }
    requesting = true;
    try {
      const requestResult = await getOrderRequestResult(currentOrderNumber);
      if (pollingSessionId.value !== sessionId) {
        return;
      }
      if (requestResult && requestResult.status === ORDER_REQUEST_CREATED) {
        stopPolling();
        loadingClose();
        submitLoading.value = false;
        currentOrderRequestId.value = '';
        goPay(currentOrderNumber)
        return;
      }
      if (requestResult && ORDER_REQUEST_FAILED_STATUS_SET.includes(requestResult.status)) {
        stopPolling();
        loadingClose();
        submitLoading.value = false;
        currentOrderRequestId.value = '';
        ElMessage.error(requestResult.failMessage || '提交订单失败，请稍后重试');
        return;
      }
      const cachedOrderNumber = await getOrderCache(currentOrderNumber);
      if (pollingSessionId.value !== sessionId || cachedOrderNumber === '') {
        return;
      }
      if (cachedOrderNumber !== currentOrderNumber) {
        console.warn('ignore mismatched order cache', {
          currentOrderNumber,
          cachedOrderNumber,
        });
        return;
      }
      stopPolling();
      //执行到这里说明订单创建成功
      //loading弹框关闭
      loadingClose();
      submitLoading.value = false;
      currentOrderRequestId.value = '';
      goPay(cachedOrderNumber)
    } catch (error) {
      console.error('poll order result failed', error);
    } finally {
      requesting = false;
    }
  }, 500); // 每500毫秒调用一次
};
//停止轮训
const stopPolling = () => {
  pollingSessionId.value += 1;
  clearInterval(pollingTimer.value);
  pollingTimer.value = null;
};

/**
 * 提交订单
 * */
function submitOrder(){
  if (submitLoading.value) {
    return;
  }

  if (ticketUserIdArr.value.length != num.value) {
    ElMessage({
      message:'选择的购票人和票张数量不一致',
      type: 'error',
    })
    return;
  }
  stopPolling();
  submitLoading.value = true;
  loadingShow();

  const orderCreateParams = {
    'programId':detailList.value.id,
    'userId':useUser.userId,
    'requestId': ensureOrderRequestId(),
    'ticketUserIdList':ticketUserIdArr.value,
    'ticketCategoryId':ticketCategoryId.value,
    'ticketCount':num.value
  }

  const createOrderVersion = import.meta.env.VITE_CREATE_ORDER_VERSION
  if (createOrderVersion == 1) {
    //v1版本的创建订单

    orderCreateV1Api(orderCreateParams).then(response => {
      loadingClose();
      if (response.code == '0') {
        const orderNumber = response.data;
        goPay(orderNumber)
      }else{
        showOrderCreateFailure(response);
      }
    }).catch(submitOrderError)
  }else if (createOrderVersion == 2) {
    //v2版本的创建订单

    orderCreateV2Api(orderCreateParams).then(response => {
      loadingClose();
      if (response.code == '0') {
        const orderNumber = response.data;
        goPay(orderNumber)
      }else{
        showOrderCreateFailure(response);
      }
    }).catch(submitOrderError)
  }else if (createOrderVersion == 3) {
    //v3版本的创建订单

    orderCreateV3Api(orderCreateParams).then(response => {
      loadingClose();
      if (response.code == '0') {
        const orderNumber = response.data;
        goPay(orderNumber)
      }else{
        showOrderCreateFailure(response);
      }
    }).catch(submitOrderError)
  }else if (createOrderVersion == 4) {
    //v4版本的创建订单

    orderCreateV4Api(orderCreateParams).then(response => {
      const orderNumber = getOrderNumberFromResponse(response);
      if (response.code == '0' && orderNumber != null) {
        console.log('异步订单创建成功 订单编号',orderNumber)
        //开始定时轮训查询
        startPolling(orderNumber,Date.now());
      }else{
        showOrderCreateFailure(response);
      }
    }).catch(submitOrderError)
  }else{
    submitOrderError();
  }
}
//弹出排队框
function dialogShow(message = '当前排队人数太多，请稍候再试~'){
  dialogMessage.value = message
  dialogVisible.value = true
  isSHowInfo.value=false
  submitLoading.value = false
}

function dialogLoading(){
  loading.value = true
  isSHowInfo.value=false
  setTimeout(() => {
    loading.value = false
    isSHowInfo.value=true
  }, 2000)
}

function loadingShow(){
  loading.value = true
  isSHowInfo.value=false
}

function loadingClose(){
  loading.value = false;
  isSHowInfo.value=true;
}

onBeforeUnmount(() => {
  stopPolling();
  submitLoading.value = false;
});
</script>

<style scoped lang="scss">
.app-container {
  width: 100%;
  height: 100%;
  background: var(--app-bg);

  .confirm-order {
    position: relative;
    box-sizing: border-box;
    display: flex;
    -webkit-box-orient: vertical;
    flex-direction: column;
    align-content: flex-start;
    flex-shrink: 0;

    .basic-info1 {
      position: relative;
      //display: flex;
      overflow: hidden;
      width: 100%;
      height: auto;

      .top {
        position: absolute;
        display: flex;
        overflow: hidden;
        -webkit-box-orient: vertical;
        flex-direction: column;
        width: 100%;
        padding-top: 31px;
        height: 318px;
        background: #111113;
        border-bottom: 7px solid var(--app-accent);

        .title {
          position: relative;
          display: flex;
          flex-shrink: 0;
          flex-grow: 0;
          margin-right: 43px;
          font-size: 37px;
          margin-left: 43px;
          width: 100%;
          max-width: 1800px;
          -webkit-box-pack: start;
          justify-content: flex-start;
          -webkit-box-align: center;
          align-items: center;
          overflow: hidden;
          color: rgb(255, 255, 255);
          font-weight: bold;
          height: auto;
        }

        .local {
          position: relative;
          display: flex;
          flex-shrink: 0;
          flex-grow: 0;
          margin-right: 43px;
          font-size: 24px;
          margin-left: 43px;
          width: fit-content;
          overflow: hidden;
          color: rgb(255, 255, 255);
          margin-top: 12px;
          height: auto;
          -webkit-box-pack: start;
          justify-content: flex-start;
          -webkit-box-align: center;
          align-items: center;
          max-width: 1800px;
        }

        .line {
          position: relative;
          display: flex;
          flex-shrink: 0;
          flex-grow: 0;
          margin-right: 43px;
          background-color: var(--app-accent);
          place-self: center flex-end;
          margin-left: 43px;
          width: 100%;
          max-width: 1800px;
          margin-top: 24px;
          height: 2px;
        }

        .time {
          position: relative;
          display: flex;
          flex-shrink: 0;
          flex-grow: 0;
          margin-right: 43px;
          font-size: 33px;
          margin-left: 43px;
          width: fit-content;
          overflow: hidden;
          color: rgb(255, 255, 255);
          margin-top: 24px;
          height: auto;
          -webkit-box-pack: start;
          justify-content: flex-start;
          -webkit-box-align: center;
          align-items: center;
          max-width: 1800px;
          flex-shrink: 0;
          flex-grow: 0;
          height: fit-content;
          span {
            white-space: pre-wrap;
            line-height: 40px;
            overflow: hidden;
            text-overflow: ellipsis;
          }
        }

        .money {
          position: relative;
          display: flex;
          flex-shrink: 0;
          flex-grow: 0;
          overflow: hidden;
          width: 100%;

          -webkit-box-orient: horizontal;
          flex-direction: row;
          margin-left: 43px;
          flex-shrink: 0;
          flex-grow: 0;
          height: fit-content;

          span{
            position: relative;
            display: flex;
            flex-shrink: 0;
            flex-grow: 0;
            font-size: 29px;
            width: fit-content;
            color: rgb(255, 255, 255);
            height: auto;
            -webkit-box-pack: start;
            justify-content: flex-start;
            -webkit-box-align: center;
            align-items: center;
            overflow: hidden;
            max-width: none;
          }
        }

        .order-info {
          position: relative;
          display: flex;
          flex-shrink: 0;
          flex-grow: 0;
          margin-right: 43px;
          font-size: 24px;
          visibility: visible;
          margin-left: 43px;
          width: 100%;
          max-width: 1800px;
          color: rgb(255, 255, 255);
          margin-top: 6px;
          -webkit-box-pack: start;
          justify-content: flex-start;
          -webkit-box-align: center;
          align-items: center;
          overflow: hidden;
          flex-shrink: 0;
          flex-grow: 0;
          height: fit-content;
        }

      }
      .bottom{
        width: 100%;
        height: auto;
        margin-top: 330px;

        .service-box{
          width: 100%;
          height: 33px;
          line-height: 33px;
          display: flex;
          flex-direction: row;
          margin-top: 30px;
          color: #000 !important;
          font-size: 24px;
          .service{
            width: 50px;
            height: 33px;
            line-height: 33px;
            margin-left: 50px;
          }
          .service-name{
            margin-left: 18px;
            width: fit-content;
            height: 33px;
            line-height: 33px;
            display: inline-block;
            .icon-warn{
              display: inline-block;
              width: 12px;
              height: 12px;
              background-repeat: no-repeat;
              background-size: 12px 12px;
              background: url('/src/assets/section/warn.png');
              margin-right: 10px;
            }
            .icon-yes-blue{
              display: inline-block;
              width: 12px;
              height: 12px;
              background-repeat: no-repeat;
              background-size: 12px 12px;
              background: url('/src/assets/section/yes-blue.png');
              margin-right: 10px;
            }
            span{
              width: fit-content;
              height: 33px;
              line-height: 33px;
            }
          }

        }
        .line{
          margin: 20px 0px 20px 50px;
          width: 97%;
          height: 2px;
          background-color: #cccccc;
          opacity: 0.7;
        }
      }
      .isRealName{
        margin-bottom: 20px;
        .left{
          position: relative;
          display: flex;
          flex: 1 1 0%;
          overflow: hidden;
          -webkit-box-orient: vertical;
          flex-direction: column;
          place-self: center flex-start;
          margin-left: 43px;
          width: fit-content;
          -webkit-box-flex: 1;
          height: auto;
          float: left;
          .title{
            position: relative;
            display: flex;
            flex-shrink: 0;
            flex-grow: 0;
            font-size: 24px;
            place-self: flex-start center;
            width: fit-content;
            height: auto;
            -webkit-box-pack: start;
            justify-content: flex-start;
            -webkit-box-align: center;
            align-items: center;
            overflow: hidden;
            max-width: none;
          }
          .notice{
            position: relative;
            display: flex;
            flex: 1 1 0%;
            font-size: 24px;
            place-self: flex-start center;
            width: fit-content;
            -webkit-box-flex: 1;
            color: var(--app-accent);
            margin-top: 6px;
            height: auto;
            -webkit-box-pack: start;
            justify-content: flex-start;
            -webkit-box-align: center;
            align-items: center;
            overflow: hidden;
            max-width: none;
          }
        }
        .right{
          float: left;
          margin-left: 43px;
          .btn{
            position: relative;
            display: flex;
            flex-shrink: 1;
            flex-grow: 0;
            overflow: hidden;
            margin-right: 43px;
            background-color: var(--app-accent);
            place-self: center flex-end;
            box-shadow: var(--app-accent) 0px 0px 0px 1px inset;
            width: 110px;
            height: 55px;
            border-radius: 28px;
            border: none;
            font-size: 24px;
          }
        }
        .ticketInfo{
          width: 100%;
          padding-left: 143.36px;
          padding-right: 143.36px;
          height: auto;
          min-height: 10.67vmin;
          //display: flex;
          -webkit-box-orient: horizontal;
          flex-direction: row;
          -webkit-box-pack: justify;
          justify-content: space-between;
          -webkit-box-align: center;
          align-items: center;
          .ticket{
            width: 100%;
            height: 136px;
            display: flex;
            flex-direction: row;
            align-items: center;
            .info{
              position: relative;
              display: flex;
              flex: 1 1 0%;
              overflow: hidden;
              -webkit-box-orient: vertical;
              flex-direction: column;
              place-self: center flex-start;
              margin-left: 43px;
              width: fit-content;
              -webkit-box-flex: 1;
              height: auto;
              float: left;
              .title{
                position: relative;
                display: flex;
                flex-shrink: 0;
                flex-grow: 0;
                place-self: flex-start center;
                width: fit-content;
                height: auto;
                -webkit-box-pack: start;
                justify-content: flex-start;
                -webkit-box-align: center;
                align-items: center;
                font-size: 4.27vmin;
                color: rgb(0, 0, 0);
                max-width: 60vmin;
                margin-right: 1.2vmin;
                overflow: hidden;
                text-overflow: ellipsis;
                white-space: nowrap;
              }
              .card{
                font-size: 3.2vmin;
                color: rgb(156, 156, 165);
                width: auto;
                overflow: hidden;
                white-space: nowrap;
                margin-right: 2.4vmin;
                //position: relative;
                //display: flex;
                //flex-direction: row;
                //flex: 1 1 0%;
                //place-self: flex-start center;
                //width: fit-content;
                //-webkit-box-flex: 1;
                //margin-top: 6px;
                //height: auto;
                //-webkit-box-pack: start;
                //justify-content: flex-start;
                //-webkit-box-align: center;
                //align-items: center;
                //overflow: hidden;
                //max-width: none;
                //font-size: 3.2vmin;
                //color: rgb(156, 156, 165);
                .cardType{
                  width: 100px;
                  font-size: 24px;
                  color:rgb(156, 156, 165);
                  display: inline-block;
                }
                .cardId{

                }
              }

            }

            .chx{}
          }

        }

      }
      .line {
        margin: 114px 0px 20px 50px;
        width: 97%;
        height: 1px;
        background-color: #cccccc;
        opacity: 0.7;
      }
      .sendMethod{
        margin-left: 43px;
        .sendMethodTitle{
          position: relative;
          display: flex;
          flex: 1 1 0%;
          margin-right: 10px;
          font-size: 24px;
          place-self: center flex-start;
          width: fit-content;
          -webkit-box-flex: 1;
          color: rgb(0, 0, 0);
          height: auto;
          -webkit-box-pack: start;
          justify-content: flex-start;
          -webkit-box-align: center;
          align-items: center;
          overflow: hidden;
          max-width: none;
        }
        .ticketType{
          position: relative;
          display: flex;
          flex-shrink: 0;
          flex-grow: 0;
          font-size: 33px;
          place-self: center flex-start;
          width: fit-content;
          color: rgb(0, 0, 0);
          height: 45px;
          -webkit-box-pack: start;
          justify-content: flex-start;
          -webkit-box-align: center;
          align-items: center;
          overflow: hidden;
          max-width: none;
          margin-top: 20px;
          margin-bottom: 10px;
          .ticketbtn {
            position: relative;
            display: flex;
            flex-shrink: 0;
            flex-grow: 0;
            font-size: 20px;
            place-self: center;
            width: fit-content;
            -webkit-box-pack: center;
            justify-content: center;
            -webkit-box-align: center;
            align-items: center;
            color: rgb(255, 146, 0);
            height: auto;
            overflow: hidden;
            max-width: none;
            border: 1px solid rgb(255, 146, 0);
            border-radius: 20px;
          }
        }
        .ticketInfo{
          position: relative;
          display: flex;
          flex-shrink: 0;
          flex-grow: 0;
          font-size: 24px;
          width: 100%;
          overflow: hidden;
          color: rgb(156, 156, 165);
          height: auto;
          -webkit-box-pack: start;
          justify-content: flex-start;
          -webkit-box-align: center;
          align-items: center;
          max-width: none;
        }
      }
      .sendline{
        margin: 20px 0px 20px 50px;
        width: 97%;
        height: 1px;
        background-color: #cccccc;
        opacity: 0.7;
      }
      .tel{
        margin-left: 43px;
        .title{
          position: relative;
          display: flex;
          flex: 1 1 0%;
          font-size: 24px;
          place-self: center flex-start;
          width: fit-content;
          -webkit-box-flex: 1;
          color: rgb(0, 0, 0);
          height: auto;
          -webkit-box-pack: start;
          justify-content: flex-start;
          -webkit-box-align: center;
          align-items: center;
          overflow: hidden;
          max-width: none;
         margin: 20px 0;
        }
        .telNum{
          width: 100%;
          height: 100%;
          outline: none;
          border: none;
          padding: 0px;
          margin: 0px;
          user-select: auto;
          font-size: 33px;
          color: rgb(0, 0, 0);
          text-align: left;
        }
      }
      .payMethod{
        margin-left: 43px;
        .title{
          position: relative;
          display: flex;
          flex: 1 1 0%;
          font-size: 24px;
          place-self: center flex-start;
          width: fit-content;
          -webkit-box-flex: 1;
          color: rgb(0, 0, 0);
          height: auto;
          -webkit-box-pack: start;
          justify-content: flex-start;
          -webkit-box-align: center;
          align-items: center;
          overflow: hidden;
          max-width: none;
          margin: 20px 0;
        }
        .payMoney{
          display: flex;
          min-height: 80px;
          align-items: center;
          span{
            font-size: 24px;
            color: #000000;
            letter-spacing: 0;
            line-height: 34px;
          }
        }
      }
      .info{
        width: 100%;
        height: 150px;
        position: fixed;
        bottom: 0px;
        background: #ffffff;
        border-top: 3px solid var(--app-accent);
        box-shadow: 0 -18px 46px rgba(24, 24, 27, .14);
        z-index: 100000;
        .descript{
          position: relative;
          display: flex;
          font-size: 22px;
          visibility: visible;
          width: fit-content;
          overflow: hidden;
          color: rgb(156, 156, 165);
          margin-top: 4px;
          height: auto;
          -webkit-box-pack: start;
          justify-content: flex-start;
          -webkit-box-align: center;
          align-items: center;
          max-width: none;
          margin-left: 43px;
        }
        .price{
          display: flex;
          flex-direction: row;
          margin: 20px 0px 20px 43px;
          .num{
            position: relative;
            display: flex;
            flex-shrink: 0;
            flex-grow: 0;
            margin-right: 6px;
            font-size: 41px;
            place-self: center flex-start;
            width: fit-content;
            color: var(--app-danger);
            height: auto;
            -webkit-box-pack: start;
            justify-content: flex-start;
            -webkit-box-align: center;
            align-items: center;
            overflow: hidden;
            max-width: none;
          }
          .detail{
            position: relative;
            display: flex;
            flex-shrink: 0;
            flex-grow: 0;
            font-size: 24px;
            place-self: center flex-start;
            width: fit-content;
            overflow: hidden;
            color: rgb(0, 0, 0);
            height: auto;
            -webkit-box-pack: start;
            justify-content: flex-start;
            -webkit-box-align: center;
            align-items: center;
            max-width: none;
          }
          .submit{
            position: absolute;
            right: 30px;
            display: flex;
            font-size: 33px;
            width: 266px;
            -webkit-box-pack: center;
            justify-content: center;
            -webkit-box-align: center;
            align-items: center;
            color: #111;
            height: 90px;
            overflow: hidden;
            max-width: none;
            border-radius: 20px;
            background: var(--app-accent);
            border: none;

          }
        }
      }
    }
  }
  .content{
    width: 100%;
    height:30px;
    line-height: 30px;
    text-align: center;
    font-size: 24px;
    margin-top: 100px;
  }
  .btn1{
    width: 300px;
    height: 50px;
    background: var(--app-primary);
    color: #FFFFFF;
    display: block;
    margin: 0 auto;
    border-radius: 50px;
    font-size: 20px;
  }
  .btn2{
    width: 300px;
   border: none;
    display: block;
    margin: 20px auto;
    background: transparent;
    font-size: 20px;
  }
}
:deep(.el-dialog){

  border-radius: 20px;
}
:deep(.el-dialog__footer){
  padding-top: 100px ;
}
:deep(.el-radio__input.is-checked .el-radio__inner) {
  border-color: var(--app-primary);
  background: var(--app-primary);
}
:deep(.el-checkbox.el-checkbox--large .el-checkbox__inner) {
  width: 4.3vmin;
  height: 4.3vmin;
  color: #dddddd;
}
:deep(.el-checkbox__input.is-checked .el-checkbox__inner ){
  background-color: var(--app-primary);
  border-color: var(--app-primary);
  font-size:4.3vmin ;
}
:deep(.el-checkbox__inner::after){
  box-sizing: content-box;
  content: "";
  border: 1px solid var(--el-checkbox-checked-icon-color);
  border-left: 0;
  border-top: 0;
  height: 30px;
  left: 9px;
  position: absolute;
  top: -3px;
  transform: rotate(45deg) scaleY(0);
  width: 19px;
  transition: transform .15s ease-in 50ms;
  transform-origin: center;
}

.app-container {
  min-height: 100vh;
  height: auto;
  padding: 24px 24px 128px;
}

.confirm-order {
  width: min(1180px, 100%);
  margin: 0 auto;
  display: block !important;
}

.confirm-order .basic-info1 {
  width: 100%;
  overflow: visible;
  border: 1px solid var(--app-border);
  border-radius: 8px;
  background: #fff;
  box-shadow: var(--app-shadow);
}

.confirm-order .basic-info1 .top {
  position: static;
  height: auto;
  padding: 30px 34px 26px;
  background:
      radial-gradient(circle at 88% 20%, rgba(245, 158, 11, .18), transparent 26%),
      linear-gradient(135deg, #111113, #18181b);
  border-bottom: 4px solid var(--app-accent);
}

.confirm-order .basic-info1 .top .title,
.confirm-order .basic-info1 .top .local,
.confirm-order .basic-info1 .top .line,
.confirm-order .basic-info1 .top .time,
.confirm-order .basic-info1 .top .money,
.confirm-order .basic-info1 .top .order-info {
  margin-left: 0;
  margin-right: 0;
  max-width: none;
}

.confirm-order .basic-info1 .top .title {
  display: block;
  font-size: 30px;
  line-height: 1.35;
  font-weight: 900;
  letter-spacing: 0;
}

.confirm-order .basic-info1 .top .local {
  margin-top: 10px;
  font-size: 17px;
  line-height: 26px;
  color: rgba(255, 255, 255, .78);
}

.confirm-order .basic-info1 .top .line {
  margin-top: 20px;
  width: 100%;
  height: 1px;
  background: rgba(245, 158, 11, .72);
}

.confirm-order .basic-info1 .top .time {
  margin-top: 20px;
  font-size: 25px;
}

.confirm-order .basic-info1 .top .time span {
  line-height: 32px;
}

.confirm-order .basic-info1 .top .money {
  margin-top: 8px;
  gap: 10px;
}

.confirm-order .basic-info1 .top .money span {
  font-size: 20px;
  line-height: 28px;
}

.confirm-order .basic-info1 .top .order-info {
  margin-top: 8px;
  font-size: 15px;
  color: rgba(255, 255, 255, .70);
}

.confirm-order .basic-info1 .bottom {
  margin-top: 0;
  padding: 22px 34px 0;
}

.confirm-order .basic-info1 .bottom .service-box {
  margin-top: 0;
  height: auto;
  line-height: normal;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  font-size: 14px;
}

.confirm-order .basic-info1 .bottom .service-box .service {
  width: auto;
  height: auto;
  line-height: normal;
  margin-left: 0;
  margin-right: 4px;
  font-size: 17px;
  font-weight: 900;
}

.confirm-order .basic-info1 .bottom .service-box .service-name {
  margin-left: 0;
  display: inline-flex;
  align-items: center;
  width: auto;
  height: 32px;
  line-height: 32px;
  padding: 0 12px;
  border-radius: 999px;
  background: var(--app-primary-soft);
  border: 1px solid var(--app-border);
  color: var(--app-text);
}

.confirm-order .basic-info1 .bottom .service-box .service-name span {
  height: auto;
  line-height: 1;
  font-size: 14px;
}

.confirm-order .basic-info1 .bottom .line,
.confirm-order .basic-info1 > .line,
.confirm-order .basic-info1 .sendline {
  margin: 22px 34px;
  width: auto;
  height: 1px;
  background: var(--app-border);
  opacity: 1;
}

.confirm-order .basic-info1 .isRealName {
  margin: 0;
  padding: 2px 34px 6px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 18px 22px;
  align-items: start;
}

.confirm-order .basic-info1 .isRealName .left {
  float: none;
  margin-left: 0;
  width: auto;
  display: block;
}

.confirm-order .basic-info1 .isRealName .left .title {
  display: block;
  font-size: 22px;
  line-height: 30px;
  font-weight: 900;
  color: var(--app-text);
}

.confirm-order .basic-info1 .isRealName .left .notice {
  display: block;
  margin-top: 6px;
  font-size: 14px;
  line-height: 22px;
  color: var(--app-text-muted);
}

.confirm-order .basic-info1 .isRealName .right {
  float: none;
  margin-left: 0;
}

.confirm-order .basic-info1 .isRealName .right .btn {
  width: auto;
  min-width: 92px;
  height: 40px;
  border-radius: 999px;
  font-size: 15px;
  font-weight: 800;
  color: #111;
}

.confirm-order .basic-info1 .isRealName .ticketInfo {
  grid-column: 1 / -1;
  width: 100%;
  padding: 0;
  min-height: 0;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 12px;
}

.confirm-order .basic-info1 .isRealName .ticketInfo .ticket {
  width: 100%;
  height: auto;
  min-height: 88px;
  padding: 16px;
  border: 1px solid var(--app-border);
  border-radius: 8px;
  background: linear-gradient(180deg, #fff, #fafafa);
  box-shadow: 0 10px 24px rgba(24, 24, 27, .05);
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 14px;
}

.confirm-order .basic-info1 .isRealName .ticketInfo .ticket .info {
  float: none;
  margin-left: 0;
  width: auto;
  display: block;
}

.confirm-order .basic-info1 .isRealName .ticketInfo .ticket .info .title {
  display: block;
  max-width: 100%;
  font-size: 17px;
  line-height: 24px;
  font-weight: 900;
  color: var(--app-text);
}

.confirm-order .basic-info1 .isRealName .ticketInfo .ticket .info .card {
  margin-top: 8px;
  font-size: 14px;
  color: var(--app-text-muted);
}

.confirm-order .basic-info1 .isRealName .ticketInfo .ticket .info .card .cardType {
  width: auto;
  margin-right: 10px;
  font-size: 14px;
  color: var(--app-text-muted);
}

.confirm-order .basic-info1 .isRealName .ticketInfo .ticket .chx {
  display: flex;
  align-items: center;
  justify-content: center;
}

.confirm-order .basic-info1 .sendMethod,
.confirm-order .basic-info1 .tel,
.confirm-order .basic-info1 .payMethod {
  margin: 0 34px;
  padding: 20px;
  border: 1px solid var(--app-border);
  border-radius: 8px;
  background: #fff;
}

.confirm-order .basic-info1 .sendMethod .sendMethodTitle,
.confirm-order .basic-info1 .tel .title,
.confirm-order .basic-info1 .payMethod .title {
  display: block;
  margin: 0 0 12px;
  font-size: 19px;
  line-height: 28px;
  font-weight: 900;
  color: var(--app-text);
}

.confirm-order .basic-info1 .sendMethod .ticketType {
  margin: 0 0 8px;
  height: auto;
  font-size: 18px;
  line-height: 28px;
  font-weight: 800;
  color: var(--app-text);
}

.confirm-order .basic-info1 .sendMethod .ticketType .ticketbtn {
  margin-left: 10px;
  height: 28px;
  font-size: 13px;
  color: #111;
  border-color: var(--app-accent);
  background: var(--app-accent-soft);
  border-radius: 999px;
}

.confirm-order .basic-info1 .sendMethod .ticketInfo,
.confirm-order .basic-info1 .payMethod .payMoney span {
  display: block;
  font-size: 14px;
  line-height: 22px;
  color: var(--app-text-muted);
}

.confirm-order .basic-info1 .tel .telNum {
  font-size: 18px;
  line-height: 28px;
  color: var(--app-text);
  font-weight: 800;
}

.confirm-order .basic-info1 .payMethod .payMoney {
  min-height: auto;
}

.confirm-order .basic-info1 .info {
  height: 112px;
  left: 0;
  right: 0;
  padding: 14px 28px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 24px;
  align-items: center;
}

.confirm-order .basic-info1 .info .descript {
  margin: 0;
  width: auto;
  max-width: 760px;
  font-size: 14px;
  line-height: 22px;
  color: var(--app-text-muted);
}

.confirm-order .basic-info1 .info .price {
  margin: 0;
  display: flex;
  align-items: center;
  gap: 10px;
}

.confirm-order .basic-info1 .info .price .num {
  font-size: 34px;
  line-height: 42px;
  font-weight: 900;
}

.confirm-order .basic-info1 .info .price .detail {
  font-size: 15px;
  color: var(--app-text);
}

.confirm-order .basic-info1 .info .price .submit {
  position: static;
  width: 210px;
  height: 58px;
  margin-left: 22px;
  border-radius: 999px;
  font-size: 24px;
  font-weight: 900;
  box-shadow: 0 18px 36px rgba(245, 158, 11, .24);
}

:deep(.el-checkbox.el-checkbox--large .el-checkbox__inner) {
  width: 22px;
  height: 22px;
}

:deep(.el-checkbox__input.is-checked .el-checkbox__inner) {
  background-color: var(--app-accent);
  border-color: var(--app-accent);
}

:deep(.el-checkbox__inner::after) {
  height: 11px;
  left: 7px;
  top: 2px;
  width: 5px;
}

.order-checkout {
  width: min(1180px, 100%);
  margin: 0 auto;
  padding: 24px 0 128px;
}

.checkout-hero {
  position: relative;
  overflow: hidden;
  border-radius: 8px;
  padding: 34px 40px 32px;
  color: #fff;
  background:
      radial-gradient(circle at 92% 18%, rgba(245, 158, 11, .22), transparent 28%),
      linear-gradient(135deg, #111113, #18181b);
  border: 1px solid rgba(255, 255, 255, .08);
  border-bottom: 5px solid var(--app-accent);
  box-shadow: var(--app-shadow);
}

.checkout-hero .hero-kicker {
  display: inline-flex;
  align-items: center;
  height: 26px;
  padding: 0 12px;
  border-radius: 999px;
  color: #111;
  background: var(--app-accent);
  font-size: 13px;
  font-weight: 900;
}

.checkout-hero h1 {
  margin: 18px 0 10px;
  font-size: 32px;
  line-height: 1.35;
  font-weight: 900;
  letter-spacing: 0;
}

.checkout-hero .hero-place {
  color: rgba(255, 255, 255, .78);
  font-size: 16px;
  line-height: 24px;
}

.checkout-hero .hero-divider {
  height: 1px;
  margin: 24px 0;
  background: rgba(245, 158, 11, .70);
}

.checkout-hero .hero-meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
  width: min(760px, 100%);
}

.checkout-hero .hero-meta > div {
  padding: 14px 16px;
  border-radius: 8px;
  background: rgba(255, 255, 255, .06);
  border: 1px solid rgba(255, 255, 255, .08);
}

.checkout-hero .meta-label {
  display: block;
  margin-bottom: 8px;
  color: rgba(255, 255, 255, .56);
  font-size: 13px;
}

.checkout-hero strong {
  font-size: 21px;
  line-height: 28px;
}

.checkout-hero p {
  margin: 16px 0 0;
  color: rgba(255, 255, 255, .72);
  font-size: 15px;
}

.checkout-card {
  margin-top: 18px;
  padding: 24px 28px;
  border: 1px solid var(--app-border);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 14px 36px rgba(24, 24, 27, .08);
}

.section-title {
  margin-bottom: 14px;
  color: var(--app-text);
  font-size: 18px;
  font-weight: 900;
}

.service-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.service-tag {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid var(--app-border);
  background: var(--app-primary-soft);
  color: var(--app-text);
  font-size: 14px;
  font-weight: 700;
}

.service-tag::before {
  content: "";
  width: 7px;
  height: 7px;
  margin-right: 8px;
  border-radius: 50%;
  background: var(--app-danger);
}

.service-tag.ok::before {
  background: var(--app-info);
}

.section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
}

.section-heading h2,
.info-card h2 {
  margin: 0;
  color: var(--app-text);
  font-size: 22px;
  line-height: 30px;
  font-weight: 900;
}

.section-heading p,
.info-card p {
  margin: 6px 0 0;
  color: var(--app-text-muted);
  font-size: 14px;
  line-height: 22px;
}

.add-user-btn {
  flex: 0 0 auto;
  height: 42px;
  padding: 0 22px;
  border: 0;
  border-radius: 999px;
  background: var(--app-accent);
  color: #111;
  font-size: 15px;
  font-weight: 900;
}

.audience-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 14px;
  margin-top: 20px;
}

.audience-card {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) auto;
  align-items: center;
  gap: 14px;
  min-height: 96px;
  padding: 16px;
  border: 1px solid var(--app-border);
  border-radius: 8px;
  background: linear-gradient(180deg, #fff, #fafafa);
  transition: border-color .2s ease, box-shadow .2s ease, transform .2s ease;
}

.audience-card.selected {
  border-color: var(--app-accent);
  box-shadow: 0 16px 34px rgba(245, 158, 11, .16);
}

.audience-card:hover {
  transform: translateY(-2px);
  border-color: rgba(245, 158, 11, .72);
}

.audience-avatar {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #111;
  background: var(--app-accent);
  font-size: 18px;
  font-weight: 900;
}

.audience-info {
  min-width: 0;
}

.audience-info strong {
  display: block;
  color: var(--app-text);
  font-size: 17px;
  line-height: 24px;
  font-weight: 900;
}

.audience-id {
  margin-top: 7px;
  color: var(--app-text-muted);
  font-size: 14px;
  line-height: 20px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.audience-id span {
  margin-right: 10px;
}

.audience-id em {
  font-style: normal;
}

.audience-empty {
  margin-top: 20px;
  padding: 24px;
  border: 1px dashed var(--app-border-strong);
  border-radius: 8px;
  color: var(--app-text-muted);
  text-align: center;
  background: var(--app-surface-soft);
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}

.info-card {
  min-height: 150px;
}

.info-card strong {
  display: block;
  margin-top: 14px;
  color: var(--app-text);
  font-size: 18px;
  line-height: 28px;
  font-weight: 900;
}

.info-card strong span {
  display: inline-flex;
  align-items: center;
  height: 26px;
  margin-left: 8px;
  padding: 0 10px;
  border-radius: 999px;
  border: 1px solid rgba(245, 158, 11, .42);
  color: #111;
  background: var(--app-accent-soft);
  font-size: 13px;
}

.checkout-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 1000;
  min-height: 96px;
  padding: 16px max(24px, calc((100vw - 1180px) / 2));
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 24px;
  align-items: center;
  background: rgba(255, 255, 255, .96);
  border-top: 3px solid var(--app-accent);
  box-shadow: 0 -18px 46px rgba(24, 24, 27, .14);
  backdrop-filter: blur(10px);
}

.checkout-note {
  max-width: 780px;
  color: var(--app-text-muted);
  font-size: 14px;
  line-height: 22px;
}

.checkout-total {
  display: flex;
  align-items: center;
  gap: 10px;
}

.total-price {
  color: var(--app-danger);
  font-size: 34px;
  line-height: 42px;
  font-weight: 900;
}

.total-detail {
  color: var(--app-text);
  font-size: 15px;
  font-weight: 700;
}

.checkout-submit {
  width: 200px;
  height: 56px;
  margin-left: 20px;
  border: 0;
  border-radius: 999px;
  background: var(--app-accent);
  color: #111;
  font-size: 23px;
  font-weight: 900;
  box-shadow: 0 18px 36px rgba(245, 158, 11, .24);
}

@media (max-width: 900px) {
  .checkout-hero .hero-meta,
  .info-grid,
  .checkout-bar {
    grid-template-columns: 1fr;
  }

  .checkout-total {
    justify-content: space-between;
  }
}

</style>
