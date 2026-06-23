<template>
  <!--个人信息-->
  <Header></Header>
  <main class="profile-page">
  <div class="section">
    <MenuSideBar class="sidebarMenu" activeIndex="3"></MenuSideBar>
    <div class="right-section">
      <div class="breadcrumb">
        <span>账户中心</span>
        <strong>个人信息</strong>
      </div>
      <div class="right-tab">
        <div class="section-title">
          <h2>基础资料</h2>
          <p>完善个人信息后，下单和观演实名校验会更顺畅。</p>
        </div>
        <div class="box">
          <div class="info-list">
            <div class="tips-info">完善更多个人信息，有助于我们为您提供更加个性化的服务，本程序将尊重并保护您的隐私。</div>
            <el-form ref="perInfoRef" :model="perInfoForm" :rules="perInfoRules" class="perInfo-form">
              <el-form-item label-width="100px" label="昵称:" prop="name">
                <el-input v-model="perInfoForm.name"/>
              </el-form-item>
              <el-form-item label-width="100px" label="真实姓名:" prop="relName">
                <el-input v-model="perInfoForm.relName"/>
              </el-form-item>
              <el-form-item label-width="100px" label="性别:" prop="gender">
                <el-radio-group v-model="perInfoForm.gender">
                  <el-radio label="1" size="large">男</el-radio>
                  <el-radio label="2" size="large">女</el-radio>
                </el-radio-group>
              </el-form-item>
              <el-form-item label-width="100px" label="身份证号:" prop="idNumber">
                <el-input v-model="perInfoForm.idNumber"/>
              </el-form-item>
              <el-button
                  size="small"
                  type="primary"
                  class="btn"
                  @click.prevent="gePersonList"
              >保存
              </el-button>

            </el-form>
          </div>
        </div>
      </div>
    </div>
  </div>
  </main>
  <Footer></Footer>
</template>

<script setup>
import MenuSideBar from '../../components/menuSidebar/index'
import Header from '../../components/header/index'
import Footer from '../../components/footer/index'
import {ref, reactive, getCurrentInstance,nextTick,onMounted } from 'vue'
import {getPersonInfo, getPersonInfoId} from '@/api/personInfo'
import useUserStore from "../../store/modules/user";
import {ElMessage} from 'element-plus'
import {getName,getUserIdKey} from "@/utils/auth";

const {proxy} = getCurrentInstance();
const useUser = useUserStore()

const perInfoForm = reactive({
  name: '',
  relName: '',
  gender: '1',
  idNumber: '',
  id: useUser.userId.value
})
const perInfoRules = ref({
  name: [
    {required: true, trigger: "blur", message: "请输入昵称"},
  ],
  gender: [
    {required: true, trigger: "blur",},
  ],
})


function gePersonList() {
  proxy.$refs.perInfoRef.validate(valid => {
    if (valid) {
      getPersonInfo(perInfoForm).then(response => {
        if (response.code == 0) {
          ElMessage({
            message: '保存成功',
            type: 'success',
          })
        }else{
            ElMessage({
              message: response.message,
              type: 'error',
            })
        }

      })

    }
  })
}

//回显
onMounted(()=>{
  nextTick(()=>{
    getPersonInfoIdList()
  })
})


async function getPersonInfoIdList() {
  const id = getUserIdKey()
  getPersonInfoId({id: id}).then(response => {
    let {gender, id, idNumber, name, relAuthenticationStatus, relName} = response.data
    perInfoForm.name = name
    perInfoForm.relName = relName
    perInfoForm.gender = gender
    perInfoForm.idNumber = idNumber
    perInfoForm.id = id
  })
}
</script>

<style scoped lang="scss">
.profile-page {
  min-height: calc(100vh - 230px);
  padding: 28px 0 56px;
  border-top: 5px solid var(--app-accent);
  background:
    linear-gradient(180deg, rgba(245, 158, 11, .10), rgba(245, 158, 11, 0) 220px),
    var(--app-bg);
}

.section {
  width: min(1180px, calc(100vw - 64px));
  margin: 0 auto;
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 26px;
  align-items: start;

  .sidebarMenu {
    position: sticky;
    top: 96px;
  }

  .right-section {
    min-width: 0;

    .breadcrumb {
      min-height: 82px;
      display: flex;
      flex-direction: column;
      justify-content: center;
      gap: 6px;
      border: 1px solid rgba(255, 255, 255, .08);
      background:
        radial-gradient(circle at 10% 0, rgba(245, 158, 11, .28), transparent 28%),
        linear-gradient(135deg, #111113, #25252b);
      border-bottom: 3px solid var(--app-accent);
      border-radius: 8px;
      padding: 18px 22px;
      color: #ffffff;

      span {
        color: var(--app-accent);
        font-size: 12px;
        font-weight: 700;
      }

      strong {
        font-size: 22px;
      }
    }

    .right-tab {
      margin-top: 18px;

      .section-title {
        margin-bottom: 14px;
        padding: 18px 20px;
        border: 1px solid var(--app-border);
        border-radius: 8px;
        background: #fff;
        box-shadow: 0 10px 26px rgba(24, 24, 27, .06);

        h2 {
          margin: 0;
          color: var(--app-text);
          font-size: 20px;
        }

        p {
          margin: 6px 0 0;
          color: var(--app-text-muted);
          font-size: 13px;
        }
      }
    }

    .box {
      border: 1px solid var(--app-border);
      border-radius: 8px;
      background: #fff;
      box-shadow: 0 10px 24px rgba(24, 24, 27, .06);

      .info-list {
        max-width: 760px;
        padding: 28px 40px 32px;
        color: #666;

        .tips-info {
          background: var(--app-accent-soft);
          border: 1px solid rgba(245, 158, 11, .35);
          padding: 10px 20px;
          color: #999;
          margin-bottom: 15px;

        }

        .btn {
          margin-left: 100px;
          background: var(--app-primary) no-repeat;
          width: 88px;
          height: 32px;
          border: 0;
          cursor: pointer;
          color: #fff;
          border-radius: 8px;

          &:hover {
            color: #111;
            background: var(--app-accent);
          }
        }
      }
    }
  }

}

:deep(.el-input__wrapper) {
  flex-grow: 0;
  width: 320px;
}

@media (max-width: 980px) {
  .section {
    width: min(100% - 24px, 1180px);
    grid-template-columns: 1fr;

    .sidebarMenu {
      position: static;
    }
  }
}

</style>
