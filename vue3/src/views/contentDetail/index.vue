<template>
  <!--点击进入单独界面详情-->
  <div class="app-container">
    <Header></Header>
    <div class="detail-page">
      <div class="wrapper">
        <div class="box-left">
          <div class="box-detail">
            <div class="count">
              <div class="box-img"><img :src="detailList.itemPicture" alt=""></div>
              <div class="order">
                <div class="title">
                  <span class="tips">电子票</span>
                  <span>{{ detailList.title }}</span>
                </div>
                <div class="address">
                  <div class="time">时间：{{ formatDateWithWeekday(detailList.showTime, detailList.showWeekTime) }}</div>
                  <div class="place">
                    <div class="addr">场馆：{{ detailList.areaName }}|{{ detailList.place }}</div>
                  </div>
                </div>
                <!--                预售-->
                <div class="notice" v-show="detailList.preSell=='1'">
                  <div class="ticket-type"><span v-if="detailList.preSell=='1'">预售</span></div>
                  <div class="content">
                    <div>{{ detailList.preSellInstruction }}</div>
                    <div class="notice-content">
                      {{ detailList.importantNotice }}
                    </div>
                  </div>
                </div>
                <div class="citys">
                  <span>城市</span>
                  <div class="city-list">
                    <div class="city-item activeCity" :id="detailList.areaId">{{ detailList.areaName }}</div>
                  </div>
                  <div class="city-more">查看更多</div>
                </div>
                <div class="order-box">
                  <div class="notice-time">场次时间均为演出当地时间</div>
                  <div class="order-time">
                    <div class="order-name">场次</div>
                    <div class="select">
                      <div class="select-list">
                        <div class="select-list-item activeCity">
                          <span>{{ formatDateWithWeekday(detailList.showTime, detailList.showWeekTime) }}</span>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
                <div class="order-box">
                  <div class="order-time">
                    <div class="order-name">票档</div>
                    <div class="select">
                      <div class="select-list" v-for="(item,index) in  ticketCategoryVoList">
                        <div class="select-list-item " @click="ticketClick(item,index)"
                             :class="{ticket: actvieIndex == index}">
                          <span>{{ item.introduce }}</span>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
                <div class="order-price">
                  <div class="num">数量</div>
                  <div class="count">
                    <div class="count-info">
                      <el-input-number v-model="num" :min="1" :max="6" @change="handleChange"/>
                    </div>
                    <div class="num-limit">每笔订单限购6张</div>
                  </div>
                </div>
                <div class="order-box">
                  <div class="order-time">
                    <div class="order-name">合计</div>
                    <div class="order-count" v-if="allPrice==''">￥{{countPrice }}</div>
                    <div class="order-count" v-else>￥{{allPrice }}</div>
                  </div>
                </div>
                <div class="buy">
                  <div class="buy-link-now" @click="nowBuy">立即购买</div>
                  <!--                    <router-link class="buy-link" to="/order/index">不，选座购买</router-link>-->
<!--                  <div class="subtitle">请您移步手机端购买</div>
                  <div class="qrcode">
                    <div class="tip">手机扫码购买更便捷</div>
                    <div class="J_qrcodeImg"></div>
                    <div class="buy-link" @click="nowBuy">不，立即购买</div>

                  </div>-->
                </div>

              </div>
            </div>
          </div>
          <div class="box-item">
            <div class="box-menu">
              <router-link class="menu-children" to="#projectDetial" @click="detialClick('#projectDetial',1)"
                           :class="{menuActive: menuActive == 1}">项目详情
              </router-link>
              <router-link class="menu-children" to="#ticketNeed" @click="detialClick('#ticketNeed',2)"
                           :class="{menuActive: menuActive == 2}">购票须知
              </router-link>
              <router-link class="menu-children" to="#watchNeed" @click="detialClick('#watchNeed',3)"
                           :class="{menuActive: menuActive == 3}">观演须知
              </router-link>
            </div>
            <div id="projectDetial">
              <div class="proDetial">活动介绍</div>
              <div v-if="isHtmlDetail" class="detail-html" v-html="detailList.detail"></div>
              <img v-else-if="detailList.detail" :src="detailList.detail" alt="">
            </div>
            <div id="ticketNeed">
              <div class="proDetial">购票须知</div>
              <ul>
                <li v-for="item in ticketNeedInfo">
                  <span>{{ item.name }}</span>
                  <div>{{ item.value }}</div>
                </li>

              </ul>
            </div>
            <div id="watchNeed">
              <div class="proDetial">观演须知</div>
              <ul>
                <li v-for="item in watchNeedInfo">
                  <span v-if="item.value!=''">{{ item.name }}</span>
                  <div v-if="item.value!=''">{{ item.value }}</div>
                </li>

              </ul>
            </div>
          </div>
        </div>
        <div class="box-right">
          <div class="service">

            <div class="sit" v-show="detailList.permitChooseSeat=='1'">查看座位图</div>
            <div class="service-note">
              <div class="service-name" v-if="detailList.permitRefund!=''">
                <i class="icon-no" v-if="detailList.permitRefund=='0'"></i><span v-if="detailList.permitRefund=='0'">不支持退</span>
                <i class="icon-yes" v-if="detailList.permitRefund=='1'"></i><span v-if="detailList.permitRefund=='1'">条件退</span>
                <i class="icon-yes" v-if="detailList.permitRefund=='2'"></i><span v-if="detailList.permitRefund=='2'">全部退</span>
              </div>
              <div class="service-desc" v-if="detailList.refundExplain!=''">{{ detailList.refundExplain }}</div>
              <div class="service-name" v-if="detailList.relNameTicketEntrance!=''">
                <i class="icon-no" v-if="detailList.relNameTicketEntrance=='0'"></i><span
                  v-if="detailList.relNameTicketEntrance=='0'">不实名购票和入场</span>
                <i class="icon-yes" v-if="detailList.relNameTicketEntrance=='1'"></i><span
                  v-if="detailList.relNameTicketEntrance=='1'">实名购票和入场</span>

              </div>
              <div class="service-desc"  v-if="detailList.relNameTicketEntranceExplain!=''">{{ detailList.relNameTicketEntranceExplain }}</div>
              <div class="service-name"   v-if="detailList.permitChooseSeat!=''">
                <i class="icon-no" v-if="detailList.permitChooseSeat=='0'"></i><span
                  v-if="detailList.permitChooseSeat=='0'">不支持选座</span>
                <i class="icon-yes" v-if="detailList.permitChooseSeat=='1'"></i><span
                  v-if="detailList.permitChooseSeat=='1'">支持选座</span>

              </div>
              <div class="service-desc"  v-if="detailList.chooseSeatExplain!=''">{{ detailList.chooseSeatExplain }}</div>
              <div class="service-name" v-if="detailList.electronicDeliveryTicket!=''">
                <i class="icon-no" v-if="detailList.electronicDeliveryTicket=='0'"></i><span
                  v-if="detailList.electronicDeliveryTicket=='0'">无票</span>
                <i class="icon-yes" v-if="detailList.electronicDeliveryTicket=='1'"></i><span
                  v-if="detailList.electronicDeliveryTicket=='1'">电子票</span>
                <i class="icon-yes" v-if="detailList.electronicDeliveryTicket=='2'"></i><span
                  v-if="detailList.electronicDeliveryTicket=='2'">快递票</span>

              </div>
              <div class="service-desc"  v-if="detailList.electronicDeliveryTicketExplain!=''">{{ detailList.electronicDeliveryTicketExplain }}</div>
              <div class="service-name"  v-if="detailList.electronicInvoice!=''">
                <i class="icon-no" v-if="detailList.electronicInvoice=='0'"></i><span
                  v-if="detailList.electronicInvoice=='0'">纸质发票</span>
                <i class="icon-yes" v-if="detailList.electronicInvoice=='1'"></i><span
                  v-if="detailList.electronicInvoice=='1'">电子发票</span>

              </div>
              <div class="service-desc"  v-if="detailList.electronicInvoiceExplain!=''">{{ detailList.electronicInvoiceExplain }}</div>
            </div>

          </div>
          <div class="box-like">
            为你推荐
          </div>
          <ul class="search__box" v-if="recommendList && recommendList.length">
            <li class="search__item" v-for="item in recommendList">
                <router-link :to="{name:'detial',params:{id:item.id}}" class="link" >
                  <img :src="item.itemPicture" alt="">
                  <router-view :key="route.fullpath" />

                </router-link>

              <div class="search_item_info">
                  <router-link :to="{name:'detial',params:{id:item.id}}"  class="link__title" >
                    <router-view :key="route.fullpath"/>
                    {{ item.title }}

                  </router-link>
                <div class="search__item__info__venue">{{ item.place }}</div>
                <div class="search__item__info__venue">{{ formatDateWithWeekday(item.showTime, item.showWeekTime) }}</div>
                <div class="search__item__info__price">￥<strong>{{ item.minPrice }}</strong> 起</div>
              </div>

            </li>
          </ul>
          <div class="recommend-empty" v-else>
            <div class="empty-mark"></div>
            <div class="empty-title">暂无推荐</div>
            <div class="empty-desc">当前城市还没有更多相似活动</div>
          </div>
        </div>
      </div>
    </div>

   <Footer></Footer>

  </div>

</template>

<script setup name="detial">
import Header from '@/components/header/index'
import Footer from '@/components/footer/index'
import {formatDateWithWeekday } from '@/utils/index'
import {useRoute, useRouter} from 'vue-router'
import {getProgramDetials} from '@/api/contentDetail'
import {computed, ref} from 'vue'
import {   useMitt } from "@/utils/index";
import {getProgramRecommendList} from "@/api/recommendlist.js"
const emitter = useMitt();
//引入reactive
import {reactive} from 'vue'
const route = useRoute();
const router = useRouter();
// 获取路由参数
const paramValue = Number(route.params.id);
const detailList = ref([])
const ticketCategoryVoList = ref([])
const actvieIndex = ref('')
const menuActive = ref('')
const ticketNeedInfo = ref([])
const watchNeedInfo = ref([])
const num = ref(1)
const countPrice = ref('')
const allPrice = ref('')
const isHtmlDetail = computed(() => typeof detailList.value.detail === 'string' && /<\/?[a-z][\s\S]*>/i.test(detailList.value.detail))
//票档id
const ticketCategoryId = ref('')
//推荐节目列表入参
const recommendParams = reactive({
  areaId: undefined,
  parentProgramCategoryId: undefined,
  programId: undefined
})
recommendParams.programId = paramValue;
//推荐列表数据
const recommendList = ref([])
getProgramDetialsList()

function getProgramDetialsList() {
  getProgramDetials({id: paramValue}).then(response => {
    detailList.value = response.data
    ticketCategoryVoList.value = detailList.value.ticketCategoryVoList
    countPrice.value=ticketCategoryVoList.value[0].price
    ticketCategoryId.value = ticketCategoryVoList.value[0].id
    allPrice.value = countPrice.value * num.value; // 初始总价 = 默认单价 × 默认数量（1）
    // allPrice.value = ''      原始代码
    ticketNeedInfo.value = [{
      name: '限购规则',
      value: detailList.value.purchaseLimitRule,
    }, {
      name: '退票/换票规则',
      value: detailList.value.refundTicketRule,
    }, {
      name: '入场规则',
      value: detailList.value.entryRule,
    }, {
      name: '儿童购票',
      value: detailList.value.childPurchase,
    }, {
      name: '发票说明',
      value: detailList.value.invoiceSpecification,
    }, {
      name: '实名购票规则',
      value: detailList.value.realTicketPurchaseRule,
    }, {
      name: '异常排单说明',
      value: detailList.value.abnormalOrderDescription,
    }]
    watchNeedInfo.value = [{
      name: '演出时长',
      value: detailList.value.performanceDuration
    }, {
      name: '入场时间',
      value: detailList.value.entryTime
    }, {
      name: '最低演出曲目',
      value: detailList.value.minPerformanceCount
    }, {
      name: '主要演员',
      value: detailList.value.mainActor
    }, {
      name: '最低演出时长',
      value: detailList.value.minPerformanceDuration
    }, {
      name: '禁止携带物品',
      value: detailList.value.prohibitedItem
    }, {
      name: '寄存说明',
      value: detailList.value.depositSpecification
    }]
  })
}

const ticketClick = (item, index) => {
    actvieIndex.value = index;
    countPrice.value = item.price; // 显式更新单价
    allPrice.value = item.price * num.value; // 总价 = 新单价 × 当前数量
    ticketCategoryId.value = item.id;
    // actvieIndex.value = index     原本代码
    // allPrice.value = item.price      原本代码
    // ticketCategoryId.value = item.id      原本代码
}
const detialClick = (url, index) => {
  menuActive.value = index


}
const handleChange = (value) => {
 const priceEach= countPrice.value
  allPrice.value = priceEach*value

}
const nowBuy=()=>{
  router.replace({path:'/order/index',state:
        {'detailList':JSON.stringify(detailList.value),'allPrice':allPrice.value,
          'countPrice':countPrice.value,'num':num.value,'ticketCategoryId':ticketCategoryId.value}})

}

getRecommendList()

//节目推荐列表
function getRecommendList(){
  getProgramRecommendList(recommendParams).then(response => {
    recommendList.value = response.data.slice(0,6);
  })
}







</script>

<style scoped lang="scss">
.app-container {
  width: 100%;
  min-height: 100%;
  overflow: auto;

  .detail-page {
    width: min(1440px, calc(100vw - 64px));
    margin: 0 auto;
    padding: 26px 0 42px;
  }

  .wrapper {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 340px;
    gap: 24px;
    align-items: start;

    .box-left {
      min-width: 0;
      background: var(--app-surface);
      border: 1px solid var(--app-border);
      border-radius: 8px;
      box-shadow: var(--app-shadow);
      overflow: hidden;

      .box-detail {
        position: relative;
        padding: 26px;
        min-height: 0;
        background:
            radial-gradient(circle at 16% 18%, rgba(245, 158, 11, .12), transparent 24%),
            linear-gradient(180deg, #fff 0%, #fafafa 100%);

        > .count {
          display: grid;
          grid-template-columns: 260px minmax(0, 1fr);
          gap: 26px;
          align-items: stretch;
          padding-left: 0;
          font-size: 22px;
          color: #000;

          .box-img {
            position: relative;
            height: 360px;
            min-height: 360px;
            border-radius: 8px;
            overflow: hidden;
            background: #111113;
            padding: 10px;
            box-shadow: 0 20px 48px rgba(24, 24, 27, .18);
            border: 1px solid rgba(24, 24, 27, .12);

            &::after {
              content: "";
              position: absolute;
              left: 0;
              right: 0;
              bottom: 0;
              height: 42%;
              background: linear-gradient(180deg, transparent, rgba(0, 0, 0, .55));
              pointer-events: none;
            }

            img {
              position: static;
              width: 100%;
              height: 100%;
              min-height: 0;
              border-radius: 6px;
              object-fit: cover;
              box-shadow: none;
              display: block;
            }
          }

          .order {
            position: relative;
            padding: 22px 26px 24px;
            background: rgba(255, 255, 255, .96);
            border: 1px solid var(--app-border);
            border-radius: 8px;
            box-shadow: 0 16px 38px rgba(24, 24, 27, .07);

            .title {
              font-size: 23px;
              line-height: 32px;
              color: var(--app-text);
              font-weight: 800;
              letter-spacing: 0;

              .tips {
                display: inline-block;
                width: auto;
                min-width: 54px;
                height: 24px;
                position: relative;
                top: -3px;
                text-align: center;
                line-height: 24px;
                //background: -webkit-linear-gradient(135deg, var(--app-primary), #ff5593);
                //background: -moz-linear-gradient(135deg, var(--app-primary) 0, #ff5593 100%);
                //background: linear-gradient(-45deg, var(--app-primary), #ff5593);
                background: var(--app-accent);
                z-index: 10;
                font-size: 14px;
                color: #111;
                border-radius: 999px;
                padding: 0 10px;
                margin-right: 8px;
                font-weight: 700;
              }

              span {

              }
            }

            .address {
              position: relative;
              font-size: 15px;
              color: var(--app-text-muted);
              margin-top: 16px;
              padding: 12px 14px;
              background: var(--app-primary-soft);
              border-radius: 8px;
              border: 1px solid var(--app-border);
              zoom: 1;

              .time {
                padding-bottom: 8px;
                color: #4b5563;
              }

              .place {
                .addr {
                  display: inline-block;
                }
              }
            }

            .notice {
              margin-top: 14px;
              padding: 12px 14px;
              font-size: 12px;
              background: #fff8e8;
              border: 1px solid rgba(245, 158, 11, .28);
              border-radius: 8px;
              position: relative;

              .ticket-type {
                display: inline-block;
                height: 24px;
                line-height: 23px;
                text-align: center;
                padding: 0 7px;
                color: #111;
                background: var(--app-accent);
                border-radius: 999px;
                margin-bottom: 10px;
                font-size: 14px;
                font-weight: 700;

                span {
                  vertical-align: middle;
                }
              }

              .content {
                -webkit-box-flex: 1;
                -webkit-flex: 1;
                -moz-box-flex: 1;
                flex: 1;
                display: -webkit-box;
                -webkit-line-clamp: 3;
                -webkit-box-orient: vertical;
                overflow: hidden;
                text-overflow: ellipsis;
                cursor: pointer;
                line-height: 1.7;

                div {

                }

                .notice-content {
                  color: var(--app-text-muted);
                }
              }
            }

            .citys {
              margin-top: 18px;
              display: grid;
              grid-template-columns: 56px minmax(0, 1fr) auto;
              align-items: start;
              column-gap: 0;

              span {
                display: inline-block;
                font-size: 15px;
                color: var(--app-text);
                line-height: 40px;
                font-weight: 700;
              }

              .city-list {
                display: block;
                font-size: 12px;
                width: auto;
                height: 40px;
                overflow: hidden;
                vertical-align: top;

                .city-item {
                  color: #000;
                  width: 78px;
                  height: 40px;
                  -webkit-box-sizing: border-box;
                  -moz-box-sizing: border-box;
                  box-sizing: border-box;
                  border: 1px solid var(--app-border);
                  text-align: center;
                  overflow: hidden;
                  text-overflow: ellipsis;
                  white-space: nowrap;
                  display: -webkit-inline-flex;
                  display: inline-flex;
                  -webkit-box-align: center;
                  -webkit-align-items: center;
                  -moz-box-align: center;
                  align-items: center;
                  -webkit-box-pack: center;
                  -webkit-justify-content: center;
                  -moz-box-pack: center;
                  justify-content: center;
                  margin-right: 6px;
                  margin-bottom: 8px;
                  cursor: pointer;
                  border-radius: 3px;
                  margin-left: 0;
                  font-weight: 700;
                }


              }

              .city-more {
                float: none;
                font-size: 12px;
                color: var(--app-text-muted);
                cursor: pointer;
                margin-top: 10px;
              }
            }

            .order-box {
              .notice-time {
                color: var(--app-text-muted);
                font-size: 12px;
                margin: 16px 0 8px;
              }

              .order-time {
                display: -webkit-box;
                display: -webkit-flex;
                display: -moz-box;
                display: flex;
                margin-top: 0px;

                .order-name {
                  display: inline-block;
                  font-size: 15px;
                  color: var(--app-text);
                  height: 48px;
                  min-width: 56px;
                  font-weight: 700;
                  line-height: 40px;
                }

                .select {
                  display: inline-block;
                  vertical-align: top;
                  margin-left: 15px;
                  -webkit-box-flex: 1;
                  -webkit-flex: 1;
                  -moz-box-flex: 1;
                  flex: 1;

                  .select-list {
                    display: -webkit-box;
                    display: -webkit-flex;
                    display: -moz-box;
                    display: flex;
                    -webkit-box-orient: horizontal;
                    -webkit-box-direction: normal;
                    -webkit-flex-direction: row;
                    -moz-box-orient: horizontal;
                    -moz-box-direction: normal;
                    flex-direction: row;
                    -webkit-flex-wrap: wrap;
                    flex-wrap: wrap;
                    float: left;

                    .select-list-item {
                      -webkit-box-orient: vertical;
                      -webkit-box-direction: normal;
                      -webkit-flex-direction: column;
                      -moz-box-orient: vertical;
                      -moz-box-direction: normal;
                      flex-direction: column;
                      -webkit-box-pack: center;
                      -webkit-justify-content: center;
                      -moz-box-pack: center;
                      justify-content: center;
                      display: -webkit-box;
                      display: -webkit-flex;
                      display: -moz-box;
                      display: flex;
                      float: left;
                      font-size: 12px;
                      color: var(--app-text);
                      padding: 10px 24px;
                      margin: 0 6px 6px 0;
                      position: relative;
                      cursor: pointer;
                      background: var(--app-surface-soft);
                      border: 1px solid rgba(0, 0, 0, .1);
                      border-color: var(--app-border);
                      border-radius: 8px;
                      transition: border-color .2s ease, color .2s ease, background .2s ease;
                      min-height: 38px;
                      font-weight: 700;

                      &:hover {
                        color: #111;
                        border-color: var(--app-accent);
                        background: var(--app-accent-soft);
                      }

                      span {
                        text-align: left;
                        margin: 1px 0;
                      }

                      .notticket {
                        background-color: transparent;
                        color: #6a7a99 !important;
                        border-color: #6a7a99 !important;
                      }
                    }
                  }
                }

                .order-count {
                  font-size: 26px;
                  color: var(--app-danger);
                  margin-left: 9px;
                  font-weight: 900;
                  line-height: 40px;
                }
                .count-detial{
                  position: relative;
                  font-size: 12px;
                  color: #000;
                  cursor: pointer;
                  margin-top: 10px;
                  margin-left: 5px;
                }
              }


            }

            .order-price {
              display: flex;

              .num {
                font-size: 15px;
                color: var(--app-text);
                height: 48px;
                flex: 0 0 56px;
                font-weight: 700;
                line-height: 40px;
              }

              .count {
                display: inline-block;
                padding-left: 0px;
                width: 340px;
                flex: 1;

                .count-info {
                  //margin-top: 30px;
                  float: left;
                }

                .num-limit {
                  font-size: 12px;
                  color: var(--app-text-muted);
                  line-height: 22px;
                  vertical-align: text-top;
                  display: inline-block;
                  float: left;
                  margin-top: 12px;
                  margin-left: 10px;
                }
              }
            }

            .buy {
              display: inline-block;
              margin-top: 20px;
              .buy-link-now{
                width: 144px;
                display: block;
                margin-bottom: 24px;
                height: 44px;
                line-height: 44px;
                font-size: 15px;
                text-align: center;
                color: #fff;
                cursor: pointer;
                background: linear-gradient(135deg, var(--app-primary), #2f2f35);
                border-radius: 999px;
                font-weight: 800;
                box-shadow: 0 14px 30px rgba(24, 24, 27, .24);
                transition: transform .2s ease, box-shadow .2s ease;

                &:hover {
                  transform: translateY(-2px);
                  background: var(--app-accent);
                  color: #111;
                  box-shadow: 0 18px 34px rgba(245, 158, 11, .25);
                }
              }


              .title {
                font-size: 18px;
                line-height: 24px;
                color: var(--app-primary);
                cursor: pointer;
              }

              .subtitle {
                margin-top: 4px;
                font-size: 12px;
                line-height: 18px;
                color: #999;

              }

              .qrcode {
                margin-top: 12px;
                border: 1px solid #ededed;
                border-radius: 12px;
                padding: 0 28px;

                .tip {
                  font-size: 14px;
                  line-height: 22px;
                  color: #333;
                  margin: 12px auto 4px;
                }

                .J_qrcodeImg {
                  display: -webkit-box;
                  display: -webkit-flex;
                  display: -moz-box;
                  display: flex;
                  -webkit-box-align: center;
                  -webkit-align-items: center;
                  -moz-box-align: center;
                  align-items: center;
                  margin-bottom: 8px;
                }

                .buy-link {
                  font-size: 12px;
                  line-height: 18px;
                  color: #999;
                  margin: 4px auto 12px;
                  text-decoration: underline;
                  cursor: pointer;
                }
              }

            }

          }
        }

      }

      .box-item {
        width: 100%;
        min-height: 800px;
        height: auto;

        .box-menu {
          height: 58px;
          line-height: 58px;
          padding-left: 28px;
          background: #fff;
          border-top: 1px solid var(--app-border);
          border-bottom: 1px solid var(--app-border);
          position: sticky;
          top: 82px;
          z-index: 10;

          .menu-children {
            font-size: 16px;
            color: var(--app-text-muted);
            margin-right: 46px;
            cursor: pointer;
            font-weight: 700;
          }

        }

        #projectDetial {
          //width: 100%;
          //height:100%;
          padding: 42px 28px 0;

          .proDetial {
            padding-bottom: 14px;
            margin-bottom: 23px;
            font-size: 20px;
            color: var(--app-text);
            border-bottom: 1px solid var(--app-border);
            font-weight: 800;
          }

          img {
            width: 100%;
            height:100%;
            display: block;
            padding-bottom: 50px;
          }

          .detail-html {
            width: 100%;
            color: #333;
            line-height: 1.8;
            overflow: hidden;

            :deep(img) {
              max-width: 100%;
              height: auto !important;
              display: block;
              margin: 0 auto 18px;
            }

          }
        }

        #ticketNeed {
          width: 100%;
          min-height: 360px;
          height: auto;
          padding: 42px 28px 0;

          .proDetial {
            padding-bottom: 14px;
            margin-bottom: 23px;
            font-size: 20px;
            color: var(--app-text);
            border-bottom: 1px solid var(--app-border);
            font-weight: 800;
          }

          ul {
            margin: 0;
            padding: 0;

            li {
              list-style: none;

              span {
                width: 100%;
                height: 20px;
                line-height: 20px;
                display: block;
                color: var(--app-text-muted);
                font-size: 13px;
              }

              div {
                line-height: 26px;
                padding-bottom: 15px;
                font-size: 16px;
                color: #3f3f46;
              }
            }
          }
        }

        #watchNeed {
          width: 100%;
          min-height: 320px;
          height: auto;
          padding: 42px 28px 0;
          //padding-bottom: 14px;
          //margin-bottom: 23px;
          //font-size: 20px;
          //color: #000;
          //border-bottom: 1px solid #e2e2e2;
          .proDetial {
            padding-bottom: 14px;
            margin-bottom: 23px;
            font-size: 20px;
            color: var(--app-text);
            border-bottom: 1px solid var(--app-border);
            font-weight: 800;
          }

          ul {
            margin: 0;
            padding: 0;

            li {
              list-style: none;

              span {
                width: 100%;
                height: 20px;
                line-height: 20px;
                display: block;
                color: var(--app-text-muted);
                font-size: 13px;
              }

              div {
                line-height: 26px;
                padding-bottom: 15px;
                font-size: 16px;
                color: #3f3f46;
              }
            }
          }
        }
      }
    }

    .box-right {
      box-sizing: border-box;
      width: 340px;
      border-left: 0;
      padding: 18px;
      float: left;
      background: var(--app-surface);
      border: 1px solid var(--app-border);
      border-radius: 8px;
      box-shadow: var(--app-shadow);
      overflow: hidden;

      .service {
        padding: 22px 18px;
        background:
            radial-gradient(circle at top right, rgba(245, 158, 11, .22), transparent 36%),
            #111113;
        border: 1px solid rgba(245, 158, 11, .20);
        border-radius: 8px;
        box-shadow: 0 18px 44px rgba(24, 24, 27, .18);

        .sit {
          display: block;
          margin-bottom: 24px;
          height: 35px;
          line-height: 35px;
          font-size: 12px;
          text-align: center;
          color: #fff;
          cursor: pointer;
          background-color: var(--app-accent);
          color: #111;
          border-radius: 8px;
        }

        .service-note {
          margin-bottom: 0;

          .service-name {
            font-size: 15px;
            margin-bottom: 8px;
            color: rgba(255, 255, 255, .88);
            font-weight: 800;
            display: flex;
            align-items: center;
            gap: 6px;

            .icon {
              display: inline-block;
              width: 12px;
              height: 12px;
              background-repeat: no-repeat;
              -webkit-background-size: 12px 12px;
              background-size: 12px 12px
            }

          }

          .service-desc {
            //margin-top: 6px;
            font-size: 12px;
            color: rgba(255, 255, 255, .58);
            margin: 0 0 14px 18px;
            line-height: 1.55;
          }
        }
      }
      .box-like{
        margin-top: 24px;
        margin-bottom: 16px;
        font-size: 20px;
        color: var(--app-text);
        line-height: 28px;
        font-weight: 900;
        display: flex;
        align-items: center;
        justify-content: space-between;

        &::after {
          content: "";
          width: 86px;
          height: 3px;
          border-radius: 999px;
          background: linear-gradient(90deg, var(--app-accent), var(--app-danger));
        }
      }
      .search__box{
        list-style: none;
        margin: 0;
        padding: 0;
          .search__item{
            width: 100%;
            min-height: 132px;
            height: auto;
            margin-bottom: 14px;
            padding: 10px;
            border: 1px solid var(--app-border);
            border-radius: 8px;
            background: #fff;
            box-shadow: 0 10px 24px rgba(24, 24, 27, .05);
            transition: transform .2s ease, box-shadow .2s ease, border-color .2s ease;
            display: grid;
            grid-template-columns: 88px minmax(0, 1fr);
            gap: 12px;

            &:hover {
              transform: translateY(-3px);
              border-color: var(--app-accent);
              box-shadow: 0 16px 34px rgba(24, 24, 27, .12);
            }
            .link{
            width: 88px;
            height: 112px;
            display: block;
              img{
                float: none;
                width: 88px;
                height: 100%;
                border-radius: 8px;
                object-fit: cover;
              }
          }
          .search_item_info{
            width: auto;
            float: none;
            min-height: 112px;
            height: auto;
            position: relative;
            .link__title{
              display: -webkit-box;
              -webkit-box-orient: vertical;
              -webkit-line-clamp: 2;
              line-clamp: 2;
              overflow: hidden;
              font-size: 14px;
              color: var(--app-text);
              padding-left: 0;
              line-height: 20px;
              font-weight: 800;
            }
            .search__item__info__venue{
              margin-top: 8px;
              color: var(--app-text-muted);
              padding-left: 0;
              font-size: 12px;
              line-height: 18px;

            }
            .search__item__info__price{
              font-size: 16px;
              color: var(--app-danger);
              margin-top: 10px;
              padding-left: 0;
              font-weight: bold;
            }
          }
        }
      }

      .recommend-empty {
        min-height: 190px;
        border: 1px dashed var(--app-border-strong);
        border-radius: 8px;
        background:
            linear-gradient(135deg, rgba(245, 158, 11, .08), transparent 42%),
            var(--app-surface-soft);
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        text-align: center;
        color: var(--app-text-muted);

        .empty-mark {
          width: 42px;
          height: 42px;
          border-radius: 50%;
          background: #111113;
          position: relative;
          margin-bottom: 12px;
          box-shadow: 0 10px 24px rgba(24, 24, 27, .18);

          &::before {
            content: "";
            position: absolute;
            left: 12px;
            top: 14px;
            width: 18px;
            height: 12px;
            border-radius: 3px;
            background: linear-gradient(135deg, var(--app-accent), var(--app-danger));
          }
        }

        .empty-title {
          color: var(--app-text);
          font-size: 15px;
          font-weight: 800;
          margin-bottom: 6px;
        }

        .empty-desc {
          font-size: 12px;
        }
      }
    }
  }


}

.active {
  border-color: var(--app-accent);
  color: #111;
  background: #fff;
}

.activeCity {
  color: #111 !important;
  border: 1px solid var(--app-accent) !important;
  background: var(--app-accent-soft) !important;
}

.ticket {
  color: #111 !important;
  border: 1px solid var(--app-accent) !important;
  background: var(--app-accent-soft) !important;
}

.menuActive {
  //position: relative;
  font-size: 20px;
  color: #000;
  border-bottom: 3px solid var(--app-accent);
}

.icon-no {
  display: inline-block;
  width: 12px;
  height: 12px;
  background-repeat: no-repeat;
  background-size: 12px 12px;
  background: url('/src/assets/section/no.png')
}

.icon-yes {
  display: inline-block;
  width: 12px;
  height: 12px;
  background-repeat: no-repeat;
  background-size: 12px 12px;
  background: url('/src/assets/section/yes.png')
}
</style>
