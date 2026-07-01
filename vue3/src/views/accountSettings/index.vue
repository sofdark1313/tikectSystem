<template>
  <!--个人信息-->
  <Header></Header>
  <main class="profile-page">
  <div class="section">
    <MenuSideBar class="sidebarMenu" activeIndex="2"></MenuSideBar>
    <div class="right-section">
      <div class="breadcrumb">
        <span>账户中心</span>
        <strong>账号设置</strong>
      </div>
      <div class="right-tab">
        <div class="section-title">
          <h2>安全设置</h2>
          <p>管理登录密码、绑定信息和实名认证状态。</p>
        </div>
        <div class="box">
          <div class="account-info" v-for="item in accountLists">
            <div class="account-row">
              <div class="account-name" :class="item.nameInfoStyle">
                <span>{{ item.nameInfo }}</span>
              </div>
              <div class="detail">{{ item.detailInfo || '建议定期检查账户安全状态' }}</div>
              <div class="explain">
                <router-link v-if="experienceAccountFlag != 1" :to="item.path"
                             :class="(item.explainInfo =='立即验证'||item.explainInfo =='立即绑定')? 'pathBtn':'btnColor'">
                  {{ item.explainInfo }}
                </router-link>
                <div class="btnColor" v-if="experienceAccountFlag == 1">
                    体验不支持
                </div>  
              </div>
            </div>
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
import useUserStore from "../../store/modules/user";
import {getName, getUserIdKey} from "../../utils/auth";
import {getPersonInfoId} from '@/api/personInfo'
import {ref, reactive} from 'vue'

//体验账号标识
const experienceAccountFlag = ref(import.meta.env.VITE_EXPERIENCE_ACCOUNT_FLAG);
let accountLists = ref([])
let telNum = ref('')

const accountList = reactive([
  {
    nameInfo: '登录密码',
    detailInfo: '',
    explainInfo: '修改',
    path: './editPassword',
    nameInfoStyle: 'name-info-yes'
  },
  {
    nameInfo: '邮箱验证',
    detailInfo: '验证邮箱可帮助您快速找回密码，并可接收订单、演出通知、促销活动等提醒',
    explainInfo: '立即绑定',
    path: './email',
    nameInfoStyle: 'name-info-yes'
  },
  {
    nameInfo: '手机验证',
    detailInfo: `您验证的手机：${telNum.value}`,
    explainInfo: '更换',
    path: './mobile',
    nameInfoStyle: 'name-info-yes'
  },
  {
    nameInfo: '实名认证',
    detailInfo: '认证您的实名信息，提高安全等级',
    explainInfo: '立即验证',
    path: './authentication',
    nameInfoStyle: 'name-info-yes'

  }
])


getIsVaild()

//通过id获取是否进行验证，为验证的话控制图标，按钮的显示
function getIsVaild() {
  const id = getUserIdKey()
  getPersonInfoId({id: id}).then(response => {
    let {relAuthenticationStatus, emailStatus,mobile} = response.data
    telNum.value = mobile
    //此处判断是否验证，来控制显示那种图标
    accountLists.value = accountList.map(item => {
      if (item.nameInfo == '邮箱验证') {
        emailStatus == "0" ? item.nameInfoStyle = 'name-info-no' : item.nameInfoStyle = 'name-info-yes'
      } else if (item.nameInfo == '实名认证') {
        relAuthenticationStatus == "0" ? item.nameInfoStyle = 'name-info-no' : item.nameInfoStyle = 'name-info-yes'
      }

      return item
    })
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
      display: flex;
      flex-direction: column;
      gap: 12px;

      .account-info {
        border: 1px solid var(--app-border);
        border-radius: 8px;
        background: #fff;
        box-shadow: 0 10px 24px rgba(24, 24, 27, .06);
        padding: 20px 22px;
      }

      .account-row {
        display: grid;
        grid-template-columns: 190px minmax(0, 1fr) 110px;
        gap: 18px;
        align-items: center;
      }

      .account-name {
        min-height: 48px;
        display: flex;
        align-items: center;
        gap: 12px;
        padding-right: 18px;
        border-right: 1px solid var(--app-border);
        color: var(--app-text);
        font-weight: 800;
        font-size: 17px;

        &::before {
          content: "";
          width: 30px;
          height: 30px;
          flex: 0 0 auto;
          border-radius: 50%;
          background: var(--app-accent-soft);
          box-shadow: inset 0 0 0 2px rgba(245, 158, 11, .35);
        }

        &.name-info-yes::before {
          background:
            linear-gradient(135deg, #22c55e, #16a34a);
          box-shadow: none;
        }

        &.name-info-no::before {
          background:
            linear-gradient(135deg, var(--app-danger), #f97316);
          box-shadow: none;
        }
      }

      .detail {
        color: var(--app-text-muted);
        font-size: 14px;
        line-height: 1.7;
      }

      .explain {
        text-align: right;

        .pathBtn,
        .btnColor {
          min-width: 82px;
          min-height: 32px;
          display: inline-flex;
          align-items: center;
          justify-content: center;
          border-radius: 8px;
          font-size: 14px;
          font-weight: 700;
          text-decoration: none;
        }

        .pathBtn {
          color: #111;
          background: var(--app-accent);
        }

        .btnColor {
          color: var(--app-text-muted);
          border: 1px solid var(--app-border);
        }
      }

    }
  }

}

.profile-page :deep(.el-input__wrapper) {
  flex-grow: 0.3
}

@media (max-width: 980px) {
  .section {
    width: min(100% - 24px, 1180px);
    grid-template-columns: 1fr;

    .sidebarMenu {
      position: static;
    }
  }

  .section .right-section .box .account-row {
    grid-template-columns: 1fr;
    gap: 12px;

    .account-name {
      border-right: none;
      padding-right: 0;
    }

    .explain {
      text-align: left;
    }
  }
}

</style>
