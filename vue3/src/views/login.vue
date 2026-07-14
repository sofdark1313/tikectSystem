<template>
  <div class="app-container">
    <Header></Header>
    <div class="main">
      <div class="login">
        <div class="main-left">
          <img :src="bg" alt="票务系统登录">
        </div>
        <div class="main-right">
          <div class="login-panel-header">
            <div class="panel-kicker">Account access</div>
            <div class="panel-title">账号登录</div>
            <div class="panel-desc">使用手机号或邮箱继续购票</div>
          </div>
          <el-form ref="loginRef" :model="loginForm" :rules="loginRules" class="login-form">
            <div class="error-tips" v-if="isTips">
              <WarningFilled style="width: 1em; height: 1em; margin-left: 8px;color: #ff934c"/>
              {{ tipsContent }}</div>
            <el-input v-model="userName" placeholder="请输入手机号或邮箱" prop="userName">
              <template #prepend>
                <el-icon :size="22" color="#111113">
                  <User/>
                </el-icon>
              </template>
            </el-input>
            <el-input type="password" show-password v-model="loginForm.password" placeholder="请输入密码"
                      prop="password">
              <template #prepend>
                <el-icon :size="22" color="#111113">
                  <Lock/>
                </el-icon>
              </template>
            </el-input>
            <el-button
                :loading="loading"
                size="large"
                type="primary"
                style="width:100%;"
                class="btn"
                @click.prevent="handleLogin"
            >
              <span v-if="!loading">登 录</span>
              <span v-else>登 录 中...</span>
            </el-button>
            <div class="login-actions">
              <div v-show="experienceAccountFlag != 1" v-if="register" class="register">
                <router-link class="link-type" :to="'/register'">立即注册</router-link>
              </div>
              <div v-show="experienceAccountFlag == 1" v-if="register" class="experienceAccount">
                <a class="link-type" @click="getExperienceAccount">点击获取体验账号</a>
              </div>
            </div>
          </el-form>
        </div>
      </div>
    </div>
    <Footer></Footer>
    <el-dialog
        v-model="stateOpen"
        title="体验账号"
        width="500"

    >
      <div class="wrapper">
        <h2 class="tip-text">扫码关注后回复：票务，获取体验账号</h2>
        <img
            class="qrcode-image"
            :src="contactQrcode"
            alt="体验账号二维码"
        />
        <div class="dialog-footer">
          <el-button class="experienceAccountConfirm" @click="stateOpen = false">确定</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import contactQrcode from '@/assets/section/contact-qrcode.png'
import bg from '@/assets/section/login-visual-generated.jpg'
import Header from '@/components/header/index'
import Footer from '@/components/footer/index'
import {isPhoneNumber, isEmailAddress} from '@/utils/index'
import {ref, getCurrentInstance} from 'vue'
import useUserStore from '@/store/modules/user'
import {useRoute, useRouter} from 'vue-router'

//体验账号标识
const experienceAccountFlag = ref(import.meta.env.VITE_EXPERIENCE_ACCOUNT_FLAG);
//获取体验账号弹出框
const stateOpen = ref(false)
const userStore = useUserStore()
const route = useRoute();
const router = useRouter();
const loading = ref(false);
// 注册开关
const register = ref(true);
const isTips = ref(false)
const tipsContent = ref('')
const {proxy} = getCurrentInstance();
const platformCode = import.meta.env.VITE_CODE || '0001'

const userName = ref('');
const loginForm = ref({
  email: '',
  mobile: '',
  password: '',
  code: platformCode // pc网站
})

const loginRules = ref({});


const handleLogin = () => {
  proxy.$refs.loginRef.validate(valid => {
    if (valid) {
      if (userName.value == '') {
        isTips.value = true
        tipsContent.value = '请输入邮箱或者手机号'
      } else if (loginForm.value.password == '') {
        tipsContent.value = '请输入密码'
        isTips.value = true
      }else{
        isTips.value = false
        //正则匹配(手机号还是邮箱涉及到传参)
        if (!identifyType(userName.value)) {
          isTips.value = true
          tipsContent.value = '请输入正确的手机号或邮箱'
          return
        }
        loading.value = true
        // 调用action的登录方法
        userStore.login(loginForm.value).then(() => {
          router.push(resolveRedirectPath());
        }).catch(() => {
          loading.value = false;
        });
      }

    }
  });
}


function identifyType(value) {
  loginForm.value.mobile = ''
  loginForm.value.email = ''
  if (isPhoneNumber(value)) {
    loginForm.value.mobile = value
    return true;
  } else if (isEmailAddress(value)) {
    loginForm.value.email = value
    return true;
  }
  return false
}

function resolveRedirectPath() {
  const redirect = route.query.redirect
  if (typeof redirect === 'string' && redirect.startsWith('/') && !redirect.startsWith('//')) {
    return redirect
  }
  return '/'
}

function getExperienceAccount(){
  stateOpen.value = true
}

</script>

<style scoped lang="scss">
.app-container {
  width: 100%;
  min-height: 100%;
  background: var(--app-bg);


  .main {
    width: 100%;
    min-height: 620px;
    background:
        linear-gradient(135deg, rgba(245, 158, 11, .18), transparent 28%),
        linear-gradient(180deg, #111113 0%, #18181b 100%);
    display: flex;
    align-items: center;

    .login {
      height: 500px;
      margin: 0 auto;
      width: 1150px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 56px;

      .main-left {
        width: 680px;

        img {
          width: 100%;
          height: 420px;
          display: block;
          object-fit: cover;
          border-radius: 8px;
          box-shadow: 0 28px 70px rgba(0, 0, 0, .42);
          border: 1px solid rgba(255, 255, 255, .14);
        }
      }

      .main-right {
        margin: 0;
        padding: 0 0 24px;
        overflow: hidden;
        width: 390px;
        min-height: 360px;
        background:
            linear-gradient(180deg, rgba(245, 158, 11, .10), transparent 42%),
            #fff;
        text-align: left;
        border-radius: 8px;
        box-shadow: 0 24px 60px rgba(0, 0, 0, .22);
        border: 1px solid rgba(255, 255, 255, .18);
        border-top: 6px solid var(--app-accent);

        .login-panel-header {
          padding: 26px 32px 18px;
          border-bottom: 1px solid var(--app-border);
          background:
              radial-gradient(circle at 90% 0, rgba(219, 39, 119, .12), transparent 30%),
              linear-gradient(180deg, #fff, #fafafa);

          .panel-kicker {
            color: var(--app-accent);
            font-size: 12px;
            font-weight: 900;
            letter-spacing: .8px;
            text-transform: uppercase;
          }

          .panel-title {
            margin-top: 8px;
            color: var(--app-text);
            font-size: 28px;
            line-height: 34px;
            font-weight: 900;
          }

          .panel-desc {
            margin-top: 8px;
            color: var(--app-text-muted);
            font-size: 14px;
          }
        }
      }
    }

  }


}

.login-form {
  width: 326px;
  margin: 24px auto 0;
}

.login-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.register a {
  display: inline-block;
  margin-left: 0;
  font-size: 14px;
  color: var(--app-primary);
  text-decoration: none;
  font-weight: 700;

  &:hover {
    color: var(--app-danger);
  }
}

.experienceAccount a {
  display: inline-block;
  margin-left: 0;
  font-size: 14px;
  color: var(--app-danger);
  text-decoration: none;
  font-weight: 700;
}

:deep(.el-input-group__prepend) {
  width: 48px;
  height: 46px;
  line-height: 46px;
  text-align: center;
  color: #111;
  position: absolute;
  left: 0;
  bottom: 0;
  background: var(--app-accent);
  border: 0;
  box-shadow: none;
  border-radius: 8px 0 0 8px;

}

.el-input-group--prepend {
  border: none;
  height: 46px;
  outline: none;
  font-size: 14px;
  padding-left: 56px;
  margin-bottom: 18px;
}

:deep(.el-input__wrapper) {
  min-height: 46px;
  border-radius: 8px;
  box-shadow: 0 0 0 1px var(--app-border) inset !important;
  transition: box-shadow .2s ease, background .2s ease;
}

:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--app-accent) inset, 0 0 0 4px rgba(245, 158, 11, .12) !important;
}

:deep(.el-input__inner) {
  font-size: 14px;
  color: var(--app-text);
}

.btn {
  background: linear-gradient(135deg, var(--app-primary), #2f2f35);
  border-color: var(--app-primary);
  border-radius: 8px;
  font-size: 18px;
  height: 46px;
  line-height: 46px;
  outline: none;
  color: #fff;
  width: 100%;
  cursor: pointer;
  font-weight: 900;
  letter-spacing: 2px;
  box-shadow: 0 16px 34px rgba(24, 24, 27, .24);
  transition: transform .2s ease, box-shadow .2s ease, background .2s ease;

  &:hover {
    transform: translateY(-1px);
    background: var(--app-accent);
    border-color: var(--app-accent);
    color: #111;
    box-shadow: 0 18px 36px rgba(245, 158, 11, .26);
  }
}
.error-tips{
  border: 1px solid #ff934c;
  background: #fefcee;
  margin-bottom: 16px;
  font-size: 14px;
  padding: 5px 8px;
  overflow: hidden;
  position: relative;
  z-index: 1001;
  text-align: left;
}
.wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  height: 100%; /* Ensure the wrapper takes full height of the dialog */
}

.qrcode-image {
  width: 240px;
  height: 240px;
  object-fit: contain;
  margin: 20px 0;
}

.dialog-footer {
  width: 100%;
  display: flex;
  justify-content: center; /* Center the button horizontally */
}

.experienceAccountConfirm{
  background-color: var(--app-primary);
  background-image: linear-gradient(90deg, var(--app-primary), #2f2f35);
  border-color: var(--app-primary);
  border-radius: 3px;
  font-size: 20px;
  height: 42px;
  line-height: 42px;
  outline: none;
  color: #fff;
  width: 20%;
  cursor: pointer;
}
</style>
