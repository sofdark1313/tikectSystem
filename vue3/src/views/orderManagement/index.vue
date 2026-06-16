<template>
<!--订单管理-->
  <Header></Header>
  <div class="red-line"></div>
  <div class="section">
    <MenuSideBar class="sidebarMenu" activeIndex="5"></MenuSideBar>
    <div class="right-section" >
      <table>
        <thead>
        <tr>
          <th style="width: 390px">项目名称</th>
          <th style="width: 144px">票品张数</th>
          <th style="width: 130px">订单金额</th>
          <th style="width: 130px">交易状态</th>
          <th  style="width: 210px">交易操作</th>
        </tr>
        </thead>
      </table>
      <div class="orderBox" v-for="(order, index) in orderList" :key="index">
          <div class="num">订单号: {{order.orderNumber}}</div>
          <ul>
            <li>
            <img :src="order.programItemPicture" alt="">
            <div class="project">
              <div class="title">{{order.programTitle}}</div>
              <div class="content">演出场次: {{order.programShowTime}}</div>
              <div class="content">演出场馆: {{order.programPlace}}</div>
            </div>
          </li>
            <li>{{ order.ticketCount }}</li>
            <li><div class="price">￥ {{ order.orderPrice }}</div><div class="money">(含运费￥0.00)</div></li>
            <li>
              <div class="orderStatus">{{ getOrderStatus(order.orderStatus) }}</div>
              <router-link class=" link" :to="{name:'orderDetail',params:{orderNumber:order.orderNumber}}"  >
                订单详情
              </router-link>
            </li>
            <li>
              <button  class="orderDetial" v-show="order.orderStatus == 1" @click="cancelOrder(order.orderNumber)">取消订单</button>
              <button  class="orderDetial" v-show="order.orderStatus == 1" @click="payOrder(order.orderNumber)">支付订单</button>
            </li>
          </ul>
          </div>

  </div>
  </div>
  <Footer class="foot"></Footer>



</template>

<script setup name="OrderManagement">
import {ref, onMounted, getCurrentInstance, nextTick, reactive} from 'vue'
import MenuSideBar from '../../components/menuSidebar/index'
import Header from '../../components/header/index'
import Footer from '../../components/footer/index'
import {useRoute,useRouter} from 'vue-router'
import {cancelOrderApi, getOrderListApi} from '@/api/order.js'
import {ElMessage} from "element-plus";
//获取用户信息
import useUserStore from "../../store/modules/user";
const router = useRouter();
//订单列表数据
const orderList = ref(0)
const useUser = useUserStore()
//订单列表入参
const orderListParams = reactive({
  userId:useUser.userId
})

//订单列表方法
const getOrderList = () => {
  getOrderListApi(orderListParams).then(response => {
    orderList.value= response.data;
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

function cancelOrder(orderNumber){
  const orderNumberParams = {orderNumber}
  cancelOrderApi(orderNumberParams).then(response => {
    if (response.code == '0') {
      ElMessage({
        message: '取消成功',
        type: 'success',
      })
      getOrderList()
    }else{
      ElMessage({
        message:response.message,
        type: 'error',
      })
    }
  })
}

function payOrder(orderNumber){
  const orderNumberText = String(orderNumber);
  localStorage.setItem('orderNumber', orderNumberText)
  router.replace({
    path:'/order/payMethod',
    query:{orderNumber: orderNumberText},
    state:{'orderNumber': orderNumberText}
  })
}

onMounted(() => {
  getOrderList()
})
</script>



<style scoped lang="scss">
.red-line {
  border-bottom: 5px solid var(--app-primary);
}

.section {
  width: 1200px;
  margin: 20px auto 0;

  .sidebarMenu {
    //width: 201px;
    float: left;
  }

  .right-section {
    width: 950px;
    height: 646px;
    margin-left: 10px;
    float: right;
    overflow-y: scroll;
    background: var(--app-surface);
    border: 1px solid var(--app-border);
    border-radius: 8px;
    box-shadow: 0 10px 24px rgba(18, 60, 70, .06);
    padding: 12px;

    table{
      width:100%;
      -webkit-box-sizing: border-box;
      box-sizing: border-box;
      border-left: 1px solid transparent;
      border-right: 1px solid transparent;
      background: var(--app-surface-soft);
      color: var(--app-text-muted);
      padding: 12px 0 12px 20px;
      height: 40px;
      line-height: 16px;
      font-size: 12px;
      margin-bottom: 20px;
      border-radius: 8px;


    }
    .orderBox{
      border: 1px solid var(--app-border);
      width: 100%;
      height: 150px;
      margin-bottom: 20px;
      border-radius: 8px;
      overflow: hidden;
      transition: box-shadow .2s ease, transform .2s ease;

      &:hover {
        transform: translateY(-2px);
        box-shadow: var(--app-shadow);
      }
      .num{
        font-size: 12px;
        padding: 14px 0 14px 20px;
        background: var(--app-surface-soft);
        color: var(--app-text-muted);
        border-bottom: 1px solid var(--app-border);
      }
      ul{
        margin: 0;
        padding: 0;
        list-style: none;
        li{
          display: flex;
          flex-direction: row;
          float: left;
          font-size: 12px;
          background: var(--app-surface);
          height: 100px;
        }
        li:first-child{
          width: 390px;
          padding-left: 20px;
          padding-top:13px;
          border-right: 1px solid var(--app-border);
          img{
            width: 62px;
            height: 80px;
            float: left;
            border-radius: 8px;
            object-fit: cover;
          }
          .project{
            width: 293px;
            padding-left: 18px;

            .title{
              width: 210px;
              color: var(--app-text);
              margin-bottom: 4px;
              display: inline-block;
              white-space: nowrap;
              overflow: hidden;
              text-overflow: ellipsis;

            }
            .content{
              color: var(--app-text-muted);
              margin-bottom: 2px;
            }

          }

        }
        li:nth-child(2){
          width: 100px;
          border-right: 1px solid var(--app-border);
          text-align: center;
          padding: 48px;
        }
        li:nth-child(3){
          width: 133px;
          display: block;
          padding-top: 32px;
          border-right: 1px solid var(--app-border);
          text-align: center;
          .price{
            width: 100%;
          }
          .money{
            width: 100%;
          }
        }
        li:nth-child(4){
          width: 133px;
          display: block;
          padding-top: 32px;
          border-right: 1px solid var(--app-border);
          text-align: center;
          .orderStatus{
            width: 100%;

          }

        }
        li:nth-child(5){
          width:168px;
          display: flex;
          flex-direction: column;
          align-items: center;
          .orderDetial{
            display: block;
            width: 98px !important;
            height: 30px;
            line-height: 30px;
            text-align: center;
            background-color: var(--app-primary);
            color: #fff;
            font-size: 14px;
            border-radius: 8px;
            margin-top: 10px;
            border: none;
            margin-bottom: 10px;
            margin-left: 0;
            cursor: pointer;
            transition: all 0.3s ease;
          }
          
          .orderDetial:hover {
            background-color: #129083;
            transform: scale(1.05);
            box-shadow: 0 8px 16px rgba(15, 118, 110, 0.24);
          }
        }
      }
    }


  }

}

.foot {

  margin-top: 676px;
}

:deep(.el-input__wrapper) {
  flex-grow: 0.3
}
.link {
  text-decoration: none; /* 去除下划线 */
}

</style>
