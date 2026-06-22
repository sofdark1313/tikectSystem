<template>
  <Header></Header>
  <el-row>
    <el-form ref="authRef" :model="authForm" :rules="authRules" class="login-form">
      <el-col :span="24">
        <el-form-item label="请输入真实姓名:" prop="relName">
          <el-input
              v-model="authForm.relName"
              class="input-with-select"
              type="password"
              show-password
          ></el-input>
        </el-form-item>
        <el-form-item label="请输入身份证号码:" prop="idNumber">
          <el-input
              v-model="authForm.idNumber"
              class="input-with-select"
              type="password"
              show-password
          ></el-input>
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
import {getUserIdKey} from "../../../utils/auth"
import {ref, reactive} from 'vue'
import {useRouter} from 'vue-router'
import useUserStore from '@/store/modules/user'
import {getAuthentication} from "../../../api/accountSettings";


const router = useRouter();
const userStore = useUserStore()
const authForm = ref({
  idNumber: '',
  relName: '',
  id: getUserIdKey()
})


const authRules = reactive({
      idNumber: [],
      relName: [],
    }
)


function savePsd() {
  getAuthentication(authForm.value).then(response => {
    if (response.code == '0') {
      ElMessage({
        message: '保存成功',
        type: 'success',
      })

      userStore.logOut().then(() => {
        location.href = '../../login';
      })

    } else {
      ElMessage({
        message: response.message,
        type: 'error',
      })
    }
  })
}
</script>

<style scoped lang="scss">
.el-row {
  width: 560px;
  min-height: 350px;
  margin: 88px auto 46px;
  padding: 42px 52px;
  background: #fff;
  border: 1px solid var(--app-border);
  border-top: 7px solid var(--app-accent);
  border-radius: 8px;
  box-shadow: var(--app-shadow);
}

.btn {
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
