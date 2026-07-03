<template>
  <Header></Header>
  <el-row>
    <el-form ref="editMobileRef" :model="editMobileForm" :rules="editMobileRules" class="login-form">
      <el-col :span="24">
        <el-form-item label="请输入手机号码:" prop="mobile">
          <el-input
              v-model="editMobileForm.mobile"
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
import {useRouter} from 'vue-router'
import useUserStore from '@/store/modules/user'
import {getEditMobile} from "../../../api/accountSettings";


const router = useRouter();
const userStore = useUserStore()
const editMobileRef = ref(null)
const editMobileForm = ref({
  mobile: '',
  id: getUserIdKey()
})

const validatePhone = (rule, value, callback) => {
  const reg = /^1[3-9]\d{9}$/;
  if (!value) {
    return callback(new Error('手机号码不能为空'));
  } else if (!reg.test(value)) {
    return callback(new Error('请输入正确的手机号码'));
  } else {
    callback();
  }
};
const editMobileRules = reactive({
      mobile: [{required: true, trigger: "blur", validator: validatePhone}]
    }
)


function savePsd() {
  editMobileRef.value.validate(valid => {
    if (!valid) {
      return
    }
    getEditMobile(editMobileForm.value).then(response => {
      if (response.code == '0') {
        ElMessage({
          message: '保存成功',
          type: 'success',
        })

        userStore.logOut().finally(() => {
          router.replace('/login');
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
