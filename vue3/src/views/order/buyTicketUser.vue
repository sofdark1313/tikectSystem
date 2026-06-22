<template>
<div class="app-container">
  <el-form ref="formTicket" :model="form" :rules="rules" class="login-form">
    <el-form-item label="姓名" prop="relName" label-width="272px" >
      <el-input
          v-model="form.relName"
          type="text"
          placeholder="请填写观演人姓名"
      ></el-input>
    </el-form-item>
    <div class="line"></div>
    <el-form-item label="证件类型" prop="idType" label-width="272px"   >
      <el-select  v-model="form.idType">
        <el-option v-for="item in idType"
                   :value="item.value"
                   :label="item.name" >{{item.name}}</el-option>
      </el-select>
    </el-form-item>
    <div class="line"></div>
    <el-form-item label="证件号码" prop="idNumber" label-width="272px"  >
      <el-input
          v-model="form.idNumber"
          type="text"
          placeholder="请填写证件号码"
      ></el-input>
    </el-form-item>
    <div class="line"></div>
    <div class="tips"><el-icon><Warning /></el-icon>点击确定表示您已阅读并同意 <span>《实名须知》</span></div>
    <div class="sure">
      <el-button  class="submit" @click="submit">确定</el-button>
    </div>
  </el-form>


</div>
</template>

<script setup name="BuyTicket">
import {getCurrentInstance, ref,onMounted} from 'vue'
import {saveTicketUser} from "@/api/buyTicketUser";
import { getUserIdKey} from "@/utils/auth";
import {useRoute, useRouter} from 'vue-router'

const route = useRoute();
const router = useRouter();

const {proxy} = getCurrentInstance();
const form=ref({})
const rules = ref({})
form.value.idType = ref('1')
const detailList = ref([])
const allPrice = ref('')
const countPrice = ref('')
const num = ref('')

const idType = ref([{
  name:'身份证',
  value:'1'
},{
  name:'港澳台居民居住证',
  value:'2'
},{
  name:'港澳台居民来往内地通行证',
  value:'3'
},{
  name:'台湾居民来往内地通行证',
  value:'4'
},{
  name:'护照',
  value:'5'
},{
  name:'歪果仁永久居住证',
  value:'6'
}])

onMounted(()=>{
  detailList.value  = JSON.parse(history.state.detailList)
  allPrice.value  = history.state.allPrice
  countPrice.value  =history.state.countPrice
  num.value  =history.state.num
})
const submit =()=>{
  proxy.$refs.formTicket.validate(valid => {
    if (valid) {
      if(form.value.relName == undefined){
        ElMessage({
          message: '请填写观演姓名',
          type: 'error',
        })
      }else if(form.value.idNumber == undefined){
        ElMessage({
          message: '请填写证件号码',
          type: 'error',
        })
      }else{
        form.value.userId=getUserIdKey()
        saveTicketUser(form.value).then(response=>{
          if(response.code==0){
            router.replace({path:'/order/index'})
          }

        })
      }

    }
  });
}














</script>

<style scoped lang="scss">
.app-container{
  min-height: 100vh;
  padding: 54px 0 120px;
  background: var(--app-bg);
  .el-form{
    width: 720px;
    margin: 0 auto;
    padding: 34px 44px 120px;
    background: #fff;
    border: 1px solid var(--app-border);
    border-top: 7px solid var(--app-accent);
    border-radius: 8px;
    box-shadow: var(--app-shadow);
    position: relative;

    .el-form-item{
      width: auto;
      height: auto;
      min-height: 72px;
      padding-left: 0;
      padding-right: 0;
      display: flex;
      -webkit-box-orient: horizontal;
      flex-direction: row;
      -webkit-box-pack: start;
      justify-content: flex-start;
      -webkit-box-align: center;
      align-items: center;

      .el-input{

      }
    }
    .line{
      width: auto;
      height: 1px;
      margin: 0 0 20px;
      background: var(--app-border);
    }
    .tips{
      width: auto;
      min-height: 44px;
      color: var(--app-text-muted);
      font-size: 14px;
      line-height: 22px;
      margin: 18px 0 0;
      padding: 12px 14px;
      display: flex;
      gap: 6px;
      -webkit-box-align: center;
      align-items: center;
      background: var(--app-accent-soft);
      border-radius: 8px;
      span{
        color: var(--app-danger);
        font-weight: 700;
      }
    }
    .sure{
      width: 100%;
      position: absolute;
      display: flex;
      -webkit-box-pack: center;
      justify-content: center;
      -webkit-box-align: end;
      align-items: flex-end;
      bottom: 0px;
      left: 0;
      height: 92px;
      padding-bottom: 24px;
      background: #fff;
      border-top: 1px solid var(--app-border);
      box-shadow: 0 -14px 32px rgba(24, 24, 27, .08);
      .submit{
        display: flex;
        -webkit-box-pack: center;
        justify-content: center;
        -webkit-box-align: center;
        align-items: center;
        width: 360px;
        height: 44px;
        font-size: 16px;
        color: #111;
        border-radius: 999px;
        border: none;
        background: var(--app-accent);
        font-weight: 800;

      }
    }
  }


}

:deep(.el-form-item--default .el-form-item__label) {

  font-size: 15px;
  color: var(--app-text);
  font-weight: 800;

}
:deep(.el-input .el-input__wrapper .el-input__inner) {
  border: none !important; /* 移除边框 */
  box-shadow: none !important; /* 移除阴影 */
}
</style>
