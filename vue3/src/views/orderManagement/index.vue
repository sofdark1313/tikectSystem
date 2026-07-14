<template>
  <Header></Header>
  <main class="order-management-page">
    <div class="section">
      <MenuSideBar class="sidebarMenu" activeIndex="5"></MenuSideBar>
      <section class="right-section">
        <div class="order-hero">
          <div>
            <p class="eyebrow">交易中心</p>
            <h1>订单管理</h1>
            <p class="hero-desc">集中查看待支付、已支付和关闭订单，未支付订单可直接继续付款。</p>
          </div>
          <div class="stats">
            <div class="stat-card">
              <span>全部订单</span>
              <strong>{{ orderCount }}</strong>
            </div>
            <div class="stat-card accent">
              <span>待支付</span>
              <strong>{{ unpaidCount }}</strong>
            </div>
          </div>
        </div>

        <div class="order-list" v-if="orderList.length">
          <article class="order-card" v-for="(order, index) in orderList" :key="order.orderNumber || index">
            <div class="order-card-top">
              <span class="order-number">订单号 {{ order.orderNumber }}</span>
              <span class="status-pill" :class="getOrderStatusClass(order.orderStatus)">
                {{ getOrderStatus(order.orderStatus) }}
              </span>
            </div>

            <div class="order-card-body">
              <div class="program-info">
                <img :src="order.programItemPicture" alt="">
                <div class="project">
                  <h2>{{ order.programTitle }}</h2>
                  <p>演出场次：{{ order.programShowTime }}</p>
                  <p>演出场馆：{{ order.programPlace }}</p>
                </div>
              </div>

              <div class="order-meta">
                <div class="meta-item">
                  <span>票品张数</span>
                  <strong>{{ order.ticketCount }}</strong>
                </div>
                <div class="meta-item">
                  <span>订单金额</span>
                  <strong class="price">￥{{ order.orderPrice }}</strong>
                  <small>含运费￥0.00</small>
                </div>
              </div>

              <div class="order-actions">
                <router-link class="detail-link" :to="{name:'orderDetail',params:{orderNumber:order.orderNumber}}">
                  订单详情
                </router-link>
                <template v-if="order.orderStatus == 1">
                  <button class="ghost-btn" @click="cancelOrder(order.orderNumber)">取消订单</button>
                  <button class="primary-btn" @click="payOrder(order.orderNumber)">立即支付</button>
                </template>
              </div>
            </div>
          </article>
        </div>

        <div class="empty-state" v-else>
          <div class="empty-mark">票</div>
          <h2>暂无订单</h2>
          <p>你还没有创建订单，去首页挑一场想看的演出。</p>
          <router-link to="/index">返回首页</router-link>
        </div>
      </section>
    </div>
  </main>
  <Footer></Footer>
</template>

<script setup name="OrderManagement">
import {computed, onMounted, ref} from 'vue'
import MenuSideBar from '../../components/menuSidebar/index'
import Header from '../../components/header/index'
import Footer from '../../components/footer/index'
import {useRouter} from 'vue-router'
import {cancelOrderApi, getOrderListApi} from '@/api/order.js'
import {ElMessage} from 'element-plus'
const router = useRouter();
//订单列表数据
const orderList = ref([])
const orderListParams = {}

//订单列表方法
const getOrderList = () => {
  getOrderListApi(orderListParams).then(response => {
    orderList.value = response.data || [];
  })
}

const orderCount = computed(() => orderList.value.length)
const unpaidCount = computed(() => orderList.value.filter(order => order.orderStatus == 1).length)

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
  return '未知状态';
}

function getOrderStatusClass(orderStatus){
  if (orderStatus == 1) {
    return 'is-unpaid';
  }
  if (orderStatus == 3) {
    return 'is-paid';
  }
  return 'is-closed';
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
.order-management-page {
  min-height: calc(100vh - 220px);
  padding: 28px 0 52px;
  background:
    linear-gradient(180deg, rgba(245, 158, 11, .10) 0, rgba(245, 158, 11, 0) 210px),
    var(--app-bg);
  border-top: 5px solid var(--app-accent);
}

.section {
  width: min(1280px, calc(100vw - 64px));
  margin: 0 auto;
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 26px;
  align-items: start;
}

.sidebarMenu {
  position: sticky;
  top: 96px;
}

.right-section {
  min-width: 0;
}

.order-hero {
  min-height: 154px;
  padding: 28px 30px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  background:
    radial-gradient(circle at 16% 18%, rgba(245, 158, 11, .28), transparent 28%),
    linear-gradient(135deg, #101014 0%, #23232a 58%, #111113 100%);
  border: 1px solid rgba(255, 255, 255, .08);
  border-radius: 8px;
  box-shadow: 0 18px 50px rgba(24, 24, 27, .20);
  color: #fff;

  .eyebrow {
    margin: 0 0 8px;
    font-size: 12px;
    letter-spacing: 0;
    color: var(--app-accent);
    font-weight: 700;
  }

  h1 {
    margin: 0;
    font-size: 32px;
    line-height: 1.2;
  }

  .hero-desc {
    margin: 12px 0 0;
    color: rgba(255, 255, 255, .68);
    font-size: 14px;
  }
}

.stats {
  display: flex;
  gap: 12px;
}

.stat-card {
  width: 112px;
  padding: 16px 14px;
  background: rgba(255, 255, 255, .08);
  border: 1px solid rgba(255, 255, 255, .10);
  border-radius: 8px;

  span {
    display: block;
    color: rgba(255, 255, 255, .62);
    font-size: 12px;
  }

  strong {
    display: block;
    margin-top: 8px;
    font-size: 28px;
    line-height: 1;
  }

  &.accent strong {
    color: var(--app-accent);
  }
}

.order-list {
  margin-top: 18px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.order-card {
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  border-radius: 8px;
  box-shadow: 0 12px 34px rgba(24, 24, 27, .08);
  overflow: hidden;
  transition: transform .18s ease, box-shadow .18s ease, border-color .18s ease;

  &:hover {
    transform: translateY(-2px);
    border-color: rgba(245, 158, 11, .52);
    box-shadow: 0 18px 46px rgba(24, 24, 27, .14);
  }
}

.order-card-top {
  min-height: 48px;
  padding: 0 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  background: #111113;
  border-bottom: 3px solid var(--app-accent);
}

.order-number {
  color: rgba(255, 255, 255, .74);
  font-size: 13px;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;

  &.is-unpaid {
    color: #7a3f00;
    background: #fff4da;
  }

  &.is-paid {
    color: #076b3c;
    background: #dcfce7;
  }

  &.is-closed {
    color: #52525b;
    background: #f4f4f5;
  }
}

.order-card-body {
  padding: 20px;
  display: grid;
  grid-template-columns: minmax(320px, 1fr) 260px 132px;
  gap: 20px;
  align-items: center;
}

.program-info {
  min-width: 0;
  display: flex;
  gap: 16px;
  align-items: center;

  img {
    width: 78px;
    height: 98px;
    flex: 0 0 auto;
    object-fit: cover;
    border-radius: 8px;
    box-shadow: 0 10px 24px rgba(24, 24, 27, .15);
  }

  .project {
    min-width: 0;

    h2 {
      margin: 0 0 10px;
      color: var(--app-text);
      font-size: 16px;
      line-height: 1.45;
      font-weight: 800;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    p {
      margin: 4px 0 0;
      color: var(--app-text-muted);
      font-size: 13px;
      line-height: 1.5;
    }
  }
}

.order-meta {
  display: grid;
  grid-template-columns: 92px 1fr;
  gap: 12px;
}

.meta-item {
  min-height: 82px;
  padding: 14px;
  border: 1px solid var(--app-border);
  border-radius: 8px;
  background: var(--app-surface-soft);
  display: flex;
  flex-direction: column;
  justify-content: center;

  span {
    color: var(--app-text-muted);
    font-size: 12px;
  }

  strong {
    margin-top: 6px;
    color: var(--app-text);
    font-size: 20px;
  }

  .price {
    color: var(--app-danger);
    font-size: 18px;
  }

  small {
    margin-top: 3px;
    color: var(--app-text-muted);
    font-size: 12px;
  }
}

.order-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.detail-link,
.ghost-btn,
.primary-btn {
  width: 100%;
  min-height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 700;
  text-decoration: none;
  cursor: pointer;
  transition: transform .18s ease, box-shadow .18s ease, background .18s ease, border-color .18s ease;
}

.detail-link,
.ghost-btn {
  color: var(--app-text);
  background: #fff;
  border: 1px solid var(--app-border);

  &:hover {
    border-color: var(--app-accent);
    color: #7a3f00;
  }
}

.primary-btn {
  border: 1px solid var(--app-accent);
  background: var(--app-accent);
  color: #111113;
  box-shadow: 0 10px 22px rgba(245, 158, 11, .24);

  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 14px 28px rgba(245, 158, 11, .30);
  }
}

.empty-state {
  margin-top: 18px;
  padding: 64px 24px;
  text-align: center;
  background: var(--app-surface);
  border: 1px dashed var(--app-border-strong);
  border-radius: 8px;
  box-shadow: var(--app-shadow);

  .empty-mark {
    width: 58px;
    height: 58px;
    margin: 0 auto 16px;
    display: grid;
    place-items: center;
    border-radius: 50%;
    background: var(--app-accent-soft);
    color: #7a3f00;
    font-weight: 800;
  }

  h2 {
    margin: 0 0 8px;
    font-size: 22px;
  }

  p {
    margin: 0 0 20px;
    color: var(--app-text-muted);
  }

  a {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 38px;
    padding: 0 18px;
    border-radius: 8px;
    background: var(--app-primary);
    color: #fff;
    text-decoration: none;
    font-weight: 700;
  }
}

@media (max-width: 1120px) {
  .section {
    grid-template-columns: 1fr;
  }

  .sidebarMenu {
    position: static;
  }

  .order-card-body {
    grid-template-columns: 1fr;
  }

  .order-actions {
    flex-direction: row;
    flex-wrap: wrap;

    > * {
      flex: 1 1 130px;
    }
  }
}

@media (max-width: 720px) {
  .order-management-page {
    padding: 18px 0 36px;
  }

  .section {
    width: min(100% - 24px, 1280px);
    gap: 16px;
  }

  .order-hero {
    padding: 22px;
    align-items: flex-start;
    flex-direction: column;

    h1 {
      font-size: 26px;
    }
  }

  .stats,
  .order-meta {
    width: 100%;
    grid-template-columns: 1fr 1fr;
  }

  .stats {
    display: grid;
  }

  .stat-card {
    width: auto;
  }

  .program-info {
    align-items: flex-start;
  }

  .order-card-body {
    padding: 16px;
  }

  .order-card-top {
    align-items: flex-start;
    flex-direction: column;
    padding: 12px 16px;
  }
}

</style>
