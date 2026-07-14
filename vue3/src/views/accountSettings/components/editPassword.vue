<template>
  <Header></Header>
  <el-row>
    <el-form ref="editPsdRef" :model="editPsdForm" :rules="editPsdRules" class="login-form">
      <el-col :span="24">
        <el-form-item label="输入密码:" prop="password" >
          <el-input
              v-model="editPsdForm.password"
              class="input-with-select"
              type="password"
              show-password
          > </el-input>
        </el-form-item>
      </el-col>
      <el-button
          size="large"
          type="primary"
          class="btn"
          @click.prevent="savePsd"
      ><span>保存</span></el-button>
    </el-form>
  </el-row>
  <Footer class="foot"></Footer>
</template>

<script setup>

import Header from '../../../components/header/index'
import Footer from '../../../components/footer/index'
import {getEditPsd} from '@/api/accountSettings'
import {ElMessage} from "element-plus"
import {ref, reactive} from 'vue'
import {useRouter} from 'vue-router'
import useUserStore from '@/store/modules/user'


const router = useRouter();
const userStore = useUserStore()
const editPsdRef = ref(null)
const editPsdForm = ref({
  password: ''
})
const editPsdRules = reactive({
  password: [
    {
      required: true,
      pattern: /^(?![\d]+$)(?![a-zA-Z]+$)(?![^\da-zA-Z]+$)([^\u4e00-\u9fa5\s]){6,20}$/,
      message: '6-20位英文字母、数字或者符号（除空格），且字母、数字和标点符号至少包含两种',
      trigger: ['blur', 'focus']
    }
  ]
})


function savePsd() {
  editPsdRef.value.validate(valid => {
    if (!valid) {
      return
    }
    getEditPsd(editPsdForm.value).then(response=>{
      if(response.code == '0'){
        ElMessage({
          message: '保存成功',
          type: 'success',
        })

        userStore.logOut().finally(() => {
          router.replace('/login');
        })

      }else{
        ElMessage({
          message: response.message,
          type: 'error',
        })
      }
    })
  })
}
</script>

<style scoped lang="scss">
.el-row{
  width: 520px;
  min-height: 320px;
  margin: 88px auto 46px;
  padding: 42px 52px;
  background: #fff;
  border: 1px solid var(--app-border);
  border-top: 7px solid var(--app-accent);
  border-radius: 8px;
  box-shadow: var(--app-shadow);
}
.btn{
  display: block;
  width: 160px;
  height: 42px;
  margin: 30px auto 0;
  background: var(--app-primary);
  border: none;
  border-radius: 8px;
  font-weight: 800;

  &:hover {
    color: #111;
    background: var(--app-accent);
  }
}

:deep(.el-form) {
  width: 100%;
}

:deep(.el-form-item__label) {
  font-weight: 700;
  color: var(--app-text);
}
</style>
