<template>
<div class="app-container" v-show="orderNumber !== '' && orderNumber !== null">
  <Header></Header>
  <div class="main">
    <el-icon :size="50" class="iconCircle"><CircleCheck color="var(--app-primary)" /></el-icon>
    <span class="paySuccess">支付成功</span>
   <div class="btn">
     <el-button  class="continueQuery" @click="continueQuery"    >继续逛逛</el-button>
     <el-button   class="orderQuery" @click="orderQuery"    >订单列表</el-button>
   </div>
  </div>
  <Footer></Footer>
</div>
</template>

<script setup name="PaySuccess">
import Header from '@/components/header/index'
import Footer from '@/components/footer/index'
import {ref, onMounted} from 'vue'
import {useRoute, useRouter} from 'vue-router'
const router = useRouter();
const route = useRoute();
const orderNumber = ref('');

//继续逛逛
const  continueQuery=()=>{
  router.replace({path:'/index'})
}
//查看订单列表
const orderQuery=()=>{
  router.replace({path:'/orderManagement/index'})
}

onMounted(()=>{
  orderNumber.value = route.query.orderNumber || history.state.orderNumber || localStorage.getItem('orderNumber')
  if (orderNumber.value) {
    localStorage.removeItem('orderNumber')
  }
  if (orderNumber.value == '' || orderNumber.value == null){
    router.replace({path:'/orderManagement/index'})
  }
})
</script>

<style scoped lang="scss">
.app-container{
  width: 1200px;
  margin: 0 auto;
  overflow: auto;
  min-height: 100vh;
  background: var(--app-bg);

  .main{
    height: 500px;
    width: 100%;
    background: var(--app-surface);
    margin: 0 auto;
    padding-top: 100px;
    text-align: center;
    position: relative;
    border: 1px solid var(--app-border);
    border-radius: 8px;
    box-shadow: var(--app-shadow);
    margin-top: 24px;
    .iconCircle{
      position: absolute;
      margin-left: -60px;
      top: 95px;
      }
    .paySuccess{
        font-size: 30px;
       font-weight: bolder;
      color: var(--app-text);

    }
    .btn{
      text-align: center;
      margin-top: 30px;
      .continueQuery{
        width: 100px;
        height: 30px;
        border-radius: 50px;
        &:hover{
          color: var(--app-primary);
          border-color:  var(--app-primary);
          background: var(--app-primary-soft);
        }
      }
      .orderQuery{
        width: 100px;
        height: 30px;
        border-radius: 50px;
        &:hover{
          color: var(--app-primary);
          border-color:  var(--app-primary);
          background: var(--app-primary-soft);
        }
      }
    }
  }
}
</style>
