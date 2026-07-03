<template>
  <Header></Header>
  <el-row>
    <el-form ref="editEmailRef" :model="editEmailForm" :rules="editEmailRules" class="login-form">
      <el-col :span="24">
        <el-form-item label="请输入邮箱:" prop="email">
          <el-input
              v-model="editEmailForm.email"
              class="input-with-select"
              type="text"
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
import {ElMessage} from "element-plus"
import {getUserIdKey} from "../../../utils/auth"
import {ref, reactive} from 'vue'
import {getEditEmail} from "../../../api/accountSettings";


const editEmailRef = ref(null)
const editEmailForm = ref({
  email: '',
  id: getUserIdKey()
})
const editEmailRules = reactive({
      email: [{
        required: true,
        pattern: /^[a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,6}$/,
        message: '请输入正确的邮箱',
        trigger: ['blur', 'focus']
      }]
    }
)


function savePsd() {
  editEmailRef.value.validate(valid => {
    if (!valid) {
      return
    }
    getEditEmail(editEmailForm.value).then(response => {
      if (response.code == '0') {
        ElMessage({
          message: '保存成功',
          type: 'success',
        })
      } else {
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
.el-row {
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
