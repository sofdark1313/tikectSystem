<template>
  <!--订单详情-->
  <div class="orderDetail">
    <Header></Header>
    <div class="app-container">
      <div class="orderNum" v-if="orderData[0]" >订单号: {{orderData[0].orderNumber}}</div>
      <div class="isPay" v-if="orderData[0]&&orderData[0].orderStatus == 1"><span>未支付</span><span>需付款: <span>￥{{orderData[0].orderPrice}}</span></span></div>
      <div class="isPay" v-if="orderData[0]&&orderData[0].orderStatus == 2">交易关闭<span>需付款: <span>￥{{orderData[0].orderPrice}}</span></span></div>
      <div class="isPay" v-if="orderData[0]&&orderData[0].orderStatus == 3"><span>已支付</span> <span>实付款: <span>￥{{orderData[0].orderPrice}}</span></span></div>
      <div class="isPay" v-if="orderData[0]&&orderData[0].orderStatus == 4">交易关闭<span>需付款: <span>￥{{orderData[0].orderPrice}}</span></span></div>
      <div class="program-table">
        <el-table :data="orderData" border style="width: 100%" class="tableCloumn">
          <el-table-column   label="项目信息"  width="400px" >
            <template #default="scope">
              <img :src="scope.row.programItemPicture" alt="">
              <div class="project">
                <div class="title">{{scope.row.programTitle}}</div>
                <div class="content">演出场次: {{scope.row.programShowTime}}</div>
                <div class="content">演出场馆: {{scope.row.programPlace}}</div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="座位信息" align="center">
            <template #default="scope" >
              <div v-for="item in scope.row.orderTicketInfoVoList">
                <span>{{item.seatInfo}}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column   label="单价"   align="center">
            <template #default="scope" >
              <div v-for="item in scope.row.orderTicketInfoVoList">
                <span>{{'￥'+item.price}}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column   label="数量"  align="center">
            <template #default="scope" >
              <div v-for="item in scope.row.orderTicketInfoVoList">
                <span>{{item.quantity}}</span>
              </div>
            </template></el-table-column>
          <el-table-column   label="优惠"  align="center">
            <template #default="scope" >
              <div v-for="item in scope.row.orderTicketInfoVoList">
                <span v-if="item.favourablePrice!=''">{{item.favourablePrice}}</span>
                <span v-else>-</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column  label="小计"  align="center"><template #default="scope" >
            <div v-for="item in scope.row.orderTicketInfoVoList">
              <span>{{item.relPrice}}</span>
            </div>
          </template></el-table-column>
        </el-table>
      </div>
      <ul v-for="item in orderData" class="orderDetialInfo" :key="'order-info-' + item.orderNumber">
        <li>
          <p class="title">配送信息</p>
          <div>配送方式：{{item.distributionMode}}</div>
          <div>取票方式：{{item.takeTicketMode}}</div>
          <div>收货人：{{getDisplayText(item.userAndTicketUserInfoVo?.userInfoVo?.relName || item.userAndTicketUserInfoVo?.userInfoVo?.name)}}</div>
          <div>手机号：{{getDisplayText(item.userAndTicketUserInfoVo?.userInfoVo?.mobile)}}</div>
        </li>
        <li>
          <p  class="title">订单信息</p>
          <div>订单编号：{{item.orderNumber}}</div>
          <div>创建时间：{{item.createOrderTime}}</div>
        </li>
        <li>
         <p  class="title">发票信息</p>
          <div>发票类型: 请在演出开始前，在程序上开具发票</div>
        </li>
        <li>
          <p  class="title">金额明细</p>
          <div> 商品总价: ￥{{item.orderPrice}}</div>
        </li>
        </ul>
      <div class="buyCustom" v-for="dict in orderData" :key="'ticket-user-' + dict.orderNumber">
        <p  class="title">购票人</p>
        <template v-if="getTicketUserInfoList(dict).length">
          <div class="info" v-for="(ticketUserInfo,index) in getTicketUserInfoList(dict)" :key="ticketUserInfo.id || index">
            <div>购票人姓名: {{getDisplayText(getTicketUserName(ticketUserInfo))}}</div>
            <div>证件类型: {{getDisplayText(getIdTypeName(getTicketUserIdType(ticketUserInfo)))}}</div>
            <div>证件号码: {{getDisplayText(getTicketUserIdNumber(ticketUserInfo))}}</div>
          </div>
        </template>
        <div v-else class="empty-info">暂无购票人信息</div>
      </div>
    </div>
    <Footer></Footer>
  </div>
</template>

<script setup name="OrderDetail">
import {ref, computed, onMounted, getCurrentInstance, nextTick, onBeforeMount} from 'vue'
import Header from '@/components/header/index'
import Footer from '@/components/footer/index'
import {useRoute} from 'vue-router'
import {getIdTypeName} from '@/api/common.js'
import {ElMessage} from "element-plus";
import {getOrderDetailApi} from '@/api/order.js'

//订单详情入参
const orderDetailParams = ref({
  orderNumber:undefined
})
//订单详情数据
const orderData = ref([])
const route = useRoute();
// 获取路由参数
orderDetailParams.value.orderNumber = route.params.orderNumber;

onMounted(()=>{
  getOrderDetail()
})
//订单详情方法
function getOrderDetail() {
  getOrderDetailApi(orderDetailParams.value).then(response => {
    orderData.value.push(response.data);
  })
}

function getOrderStatus(orderStatus){
  if (orderStatus == 1) {
    return '未支付';
  }
  if (orderStatus == 2) {
    return '交易关闭';
  }
  if (orderStatus == 3) {
    return '已支付';
  }
  if (orderStatus == 4) {
    return '交易关闭';
  }
}

function hasDisplayValue(value) {
  return value !== undefined && value !== null && String(value).trim() !== '';
}

function getDisplayText(value) {
  return hasDisplayValue(value) ? value : '-';
}

function getTicketUserInfoList(order) {
  const userAndTicketUserInfo = order?.userAndTicketUserInfoVo || {};
  const ticketUserList = userAndTicketUserInfo.ticketUserInfoVoList ||
      userAndTicketUserInfo.ticketUserVoList ||
      order?.ticketUserInfoVoList ||
      order?.ticketUserVoList ||
      [];

  if (!Array.isArray(ticketUserList)) {
    return [];
  }
  return ticketUserList.filter(ticketUserInfo => {
    return ticketUserInfo && (
        hasDisplayValue(getTicketUserName(ticketUserInfo)) ||
        hasDisplayValue(getTicketUserIdType(ticketUserInfo)) ||
        hasDisplayValue(getTicketUserIdNumber(ticketUserInfo))
    );
  });
}

function getTicketUserName(ticketUserInfo) {
  return ticketUserInfo?.relName || ticketUserInfo?.rel_name || ticketUserInfo?.name;
}

function getTicketUserIdType(ticketUserInfo) {
  return ticketUserInfo?.idType || ticketUserInfo?.id_type;
}

function getTicketUserIdNumber(ticketUserInfo) {
  return ticketUserInfo?.idNumber || ticketUserInfo?.id_number || ticketUserInfo?.cardNumber;
}
</script>

<style scoped lang="scss">
.orderDetail{
  width: 100%;
  height: 100%;
  background: var(--app-bg);
  .app-container{
    width: 1200px;
    margin: 0 auto;
    padding: 26px 0 42px;
    .orderNum{
      display: inline-flex;
      align-items: center;
      min-height: 36px;
      margin-top: 0;
      font-size: 16px;
      margin-bottom: 14px;
      padding: 0 16px;
      color: rgba(255, 255, 255, .78);
      background: #111113;
      border-radius: 999px;
    }
    .isPay{
      font-size: 28px;
      display: flex;
      flex-direction: row;
      margin-bottom: 20px;
      padding: 26px 30px;
      background: #111113;
      border-radius: 8px;
      border-bottom: 7px solid var(--app-accent);
      color: #fff;
      box-shadow: 0 18px 46px rgba(24, 24, 27, .18);
      span:first-child{
      flex-grow: 0.8;
      font-weight: 800;
      }
      span:last-child{
        font-size: 14px;
        flex-grow: 0.2;
        span{
          font-size: 30px;
          color: var(--app-accent);
          font-weight: 800;
        }
      }
    }
    .program-table{
      background: #fff;
      border: 1px solid var(--app-border);
      border-radius: 8px;
      overflow: hidden;
      box-shadow: var(--app-shadow);
      .tableCloumn{
        img{
          width: 62px;
          height: 80px;
          float: left;
          border-radius: 8px;
          object-fit: cover;
        }
        .project{
          width: 293px;
          padding-left:68px;

          .title{
            width:315px;
            color: #4a4a4a;
            margin-bottom: 4px;
            display: inline-block;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;

          }
          .content{
            color: #9b9b9b;
            margin-bottom: 2px;
          }

        }
      }

    }
    .orderDetialInfo{
      margin: 0;
      padding: 0;
      width: 100%;
      height: 180px;
      border: 1px solid var(--app-border);
      margin-top: 30px;
      background: #fff;
      border-radius: 8px;
      overflow: hidden;
      box-shadow: 0 12px 28px rgba(24, 24, 27, .08);
      li{
        width: 25%;
        height: 100%;
        border-right: 1px solid var(--app-border);
        list-style: none;
        float: left;
        .title{
          width: 100%;
          height: 20px;
          display: block;
          font-size: 20px;
          padding-left: 20px;
          font-weight: 800;
          color: var(--app-text);
        }
        div{
          padding: 2px 2px 2px 10px;
          font-size: 14px;


        }
      }
    }
    .buyCustom{
      width: 100%;
      min-height: 180px;
      margin-top: 20px;
      margin-bottom: 20px;
      border: 1px solid #f5f7fa;
      border-color: var(--app-border);
      background: #fff;
      border-radius: 8px;
      box-shadow: 0 12px 28px rgba(24, 24, 27, .08);
      .title{
        width: 100%;
        height: 20px;
        display: block;
        font-size: 20px;
        padding-left: 20px;
        font-weight: 800;
      }
      .info{
        width: 25%;
        min-width: 220px;
        min-height: 110px;
        margin-top: 10px;
        display: inline-block;
        vertical-align: top;
        div{
          padding: 2px 2px 2px 10px;
          font-size: 14px;
          color: var(--app-text-muted);
        }
      }
      .empty-info{
        padding: 12px 20px;
        color: var(--app-text-muted);
        font-size: 14px;
      }
    }
  }

}

:deep(.el-table th.el-table__cell) {
  background: #111113 !important;
  color: rgba(255, 255, 255, .78) !important;
}

:deep(.el-table--border .el-table__cell) {
  border-color: var(--app-border);
}

</style>
