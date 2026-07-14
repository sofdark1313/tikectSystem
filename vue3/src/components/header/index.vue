<template>
  <div class="app-header">
    <div class="header">
      <router-link to="/index" class="link">
        <span class="brand-mark">
          <span class="brand-ticket"></span>
        </span>
        <span class="brand-copy">
          <strong>TICKET</strong>
          <em>Platform</em>
        </span>
      </router-link>
      <div class="localHeader" v-if="isShowHeader">
        <el-icon :size="16">
          <Location/>
        </el-icon>

        <el-popover placement="bottom" @show="loadCityOptions" style="height: 10px">
          <template #reference>
            <span style="margin-right: 16px" class="city-location">{{ localName }}<el-icon :size="12"> <CaretBottom/></el-icon></span>
          </template>
          <div class="city">
            <div class="now-city">
              <span class="title-city">当前城市：</span><span class="city-name select-city">{{ localName }}</span>
            </div>
            <div class="hot-city">
              <span class="title-city">热门城市：</span>
              <ul class="list-city">
                <li class="city-name" v-for="item in hotCity" :key="item.id" @click="getCityInfoList(item)">
                  {{ item.name }}
                </li>
              </ul>
            </div>
            <div class="others-city">
              <span class="title-city">其他城市：</span>
              <ul class="list-city">
                <li class="city-name" v-for="item in otherCity" :key="item.id" @click="getCityInfoList(item)">
                  {{ item.name }}
                </li>
              </ul>
            </div>
          </div>
        </el-popover>


      </div>
      <div class="recommendHeader" v-if="isShowHeader">
        <router-link to="/index" class="routeHome" tag="div">首页</router-link>
        <router-link to="/allType/index" class="routeType" tag="div">分类</router-link>
      </div>
      <div class="searchHeader" v-if="isShowHeader">
        <el-input
            v-model="iptSearch"
            placeholder="搜索明星、演出、体育赛事"
            class="input-with-search"
        >
          <template #prepend>
            <el-icon :size="20">
              <Search/>
            </el-icon>
          </template>
          <template #append>
            <el-button class="searchBtn" @click="getProgramSearchList">搜索</el-button>
          </template>
        </el-input>
      </div>
      <div class="rightHeader" v-if="isShowHeader">
        <div class="box-left">
          <el-popover :width="150" popper-class="user-menu-popover">
            <template #reference>
              <router-link v-if="!isHasToken" to="/login" class="user-trigger">
                <span class="user-avatar">
                  <el-icon :size="17"><UserFilled /></el-icon>
                </span>
                <span class="user-name">登录</span>
              </router-link>
              <button v-else class="user-trigger" type="button">
                <span class="user-avatar">
                  <el-icon :size="17"><UserFilled /></el-icon>
                </span>
                <span class="user-name">{{ isLoginToken }}</span>
                <el-icon :size="12" class="user-arrow"><CaretBottom /></el-icon>
              </button>
            </template>
            <template #default>
              <ul class="loginInfo" v-if="isHasToken">
                <li>
                  <router-link to="/personInfo/index">个人信息</router-link>
                </li>
                <li>
                  <router-link to="/accountSettings/index">账号设置</router-link>
                </li>
                <li>
                  <router-link to="/orderManagement/index">订单管理</router-link>
                </li>
                <li @click="loginOut" class="logOut" v-if="isHasToken">
                  <span class="loginOut">退出登录</span>
                </li>
              </ul>
              <router-link v-else to="/login" class="login-popover-entry">去登录</router-link>
            </template>
          </el-popover>

        </div>
        <div class="box-right">
          <a href="/help.html" target="_blank" class="help-link">帮助</a>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>

import {ref, onMounted} from 'vue'
import {getUserIdKey} from "../../utils/auth";
import useUserStore from '@/store/modules/user'
import {getPersonInfoId} from '@/api/personInfo'
import {useRoute, useRouter} from 'vue-router'
import {getCurrentCity, getHotCity, getOtherCity, getCityInfo} from '@/api/area'
import {defineEmits} from 'vue'
import {getProgramSearch} from "@/api/allType";
import {useMitt} from "@/utils/index";

const emitter = useMitt();

const route = useRoute()
const router = useRouter()
const isLoginToken = ref('登录')
const isHasToken = ref(false)
const iptSearch = ref('')
const isShowHeader = ref(true)
const userStore = useUserStore()
const localName = ref('')
const localId = ref('')
const hotCity = ref([])
const otherCity = ref([])
const cityOptionsLoaded = ref(false)
const queryParams = ref({
  content: '',
  pageNumber: 1,
  pageSize: 10,
  timeType: 0
})
const emits = defineEmits(['updateValue'])
//初始化判断是否是登录界面，是否显示登录搜索等功能
const path = route.path
if (path == '/login') {
  isShowHeader.value = false
} else {
  isShowHeader.value = true
}

//退出到首页,设置token为空，并且昵称变为登录
function loginOut() {
  userStore.logOut().finally(() => {
    isLoginToken.value = '登录'
    isHasToken.value = false
    router.replace('/')
  })

}

//初始化如果cookie存在id，通过id获取昵称，回显到登录位置
if (getUserIdKey()) {
  getNickName()
}

function getNickName() {
  getPersonInfoId({}).then(response => {
    if (response.data != null) {
      let {name} = response.data
      isLoginToken.value = name
      if (isLoginToken.value && isLoginToken.value.length > 2) {
        isLoginToken.value = isLoginToken.value.slice(0,2)+"..."
      }
      isHasToken.value = true
    }
  })
}

onMounted(() => {
  getCurrent()
})

//当前城市
function getCurrent() {
  getCurrentCity().then(response => {
    let {name, parentId, id, type} = response.data
    localName.value = name
    localId.value = id
    emits('updateValue', localId.value)
  })

}


//热门城市
function getHot() {
  return getHotCity().then(response => {
    hotCity.value = response.data

  })

}

//其他城市
function getOther() {
  return getOtherCity().then(response => {
    otherCity.value = response.data
  })
}

function loadCityOptions() {
  if (cityOptionsLoaded.value) {
    return
  }
  Promise.all([getHot(), getOther()]).then(() => {
    cityOptionsLoaded.value = true
  })
}

/**
 * 点击改变当前地点后，获取初始化接口更新地点
 * @param params
 */
function getCityInfoList(params) {
  getCityInfo({id: params.id}).then(response => {
    let {name, parentId, id, type} = response.data
    localName.value = name
    localId.value = id
    emits('updateValue', localId.value)
  })
}
function getProgramSearchList() {
  queryParams.value.content = iptSearch.value
  getProgramSearch(queryParams.value).then(response => {
    emitter.emit('searchList',response.data)
    router.push({path: "/allType/index"});
  })
}

</script>

<style scoped lang="scss">
.app-header {
  width: 100%;
  height: 82px;
  background: #111113;
  border-bottom: 3px solid var(--app-accent);
  box-shadow: 0 18px 46px rgba(24, 24, 27, .22);
  position: sticky;
  top: 0;
  z-index: 1000;

  .header {
    width: min(1240px, calc(100vw - 64px));
    margin: 0 auto;
    height: 82px;
    display: grid;
    grid-template-columns: 144px 92px 162px minmax(320px, 440px) auto;
    align-items: center;
    gap: 18px;

    .link {
      display: inline-flex;
      align-items: center;
      gap: 10px;
      float: none;
      width: 142px;
      height: 44px;
      flex: 0 0 142px;
      border-radius: 999px;
      background: linear-gradient(135deg, rgba(255, 255, 255, .10), rgba(255, 255, 255, .04));
      border: 1px solid rgba(245, 158, 11, .42);
      padding: 0 14px 0 12px;
      box-shadow: inset 0 0 0 1px rgba(255, 255, 255, .04), 0 12px 30px rgba(0, 0, 0, .28);
      transition: border-color .2s ease, transform .2s ease, box-shadow .2s ease;

      &:hover {
        transform: translateY(-1px);
        border-color: var(--app-accent);
        box-shadow: inset 0 0 0 1px rgba(255, 255, 255, .06), 0 16px 34px rgba(0, 0, 0, .36);
      }

      .brand-mark {
        width: 30px;
        height: 30px;
        flex: 0 0 30px;
        border-radius: 50%;
        background: #fff7e6;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        position: relative;
        box-shadow: 0 0 0 3px rgba(245, 158, 11, .12);
      }

      .brand-ticket {
        width: 17px;
        height: 12px;
        border-radius: 3px;
        background: linear-gradient(135deg, var(--app-accent), var(--app-danger));
        display: block;
        position: relative;

        &::before,
        &::after {
          content: "";
          position: absolute;
          top: 4px;
          width: 4px;
          height: 4px;
          border-radius: 50%;
          background: #fff7e6;
        }

        &::before {
          left: -2px;
        }

        &::after {
          right: -2px;
        }
      }

      .brand-copy {
        display: flex;
        flex-direction: column;
        justify-content: center;
        min-width: 0;
        line-height: 1;

        strong {
          color: #fff;
          font-size: 15px;
          font-weight: 900;
          letter-spacing: .8px;
        }

        em {
          margin-top: 4px;
          color: rgba(255, 255, 255, .56);
          font-size: 10px;
          font-style: normal;
          letter-spacing: .2px;
        }
      }
    }

    .localHeader {
      width: auto;
      height: 100%;
      float: none;
      position: relative;
      margin-left: 0;
      display: inline-flex;
      align-items: center;
      line-height: 1;
      white-space: nowrap;
      cursor: pointer;
      color: rgba(255, 255, 255, .72);

      .city-location {
        max-width: 68px;
        font-size: 16px;
        color: #fff;
        display: inline-flex;
        align-items: center;
        margin-left: 5px;
        margin-right: 0;
        //white-space: nowrap;
        //overflow: hidden;
        //text-overflow: ellipsis;
        border: none;

        &:hover {
          background: none;
        }
      }


    }

    .recommendHeader {
      height: auto;
      float: none;
      margin: 0;
      line-height: 1;
      overflow: hidden;
      display: inline-flex;
      align-items: center;
      gap: 8px;
      justify-content: center;

      .routeHome {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        font-size: 16px;
        min-width: 58px;
        height: 36px;
        margin-right: 0;
        padding: 0 14px;
        overflow: hidden;
        color: rgba(255, 255, 255, .74);
        font-weight: 600;
        border-radius: 999px;
        line-height: 1;

      }

      .routeHome.router-link-active {
        color: #111;
        background: var(--app-accent);
      }

      .routeType {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        font-size: 16px;
        color: rgba(255, 255, 255, .74);
        min-width: 58px;
        height: 36px;
        margin-right: 0;
        padding: 0 14px;
        overflow: hidden;
        font-weight: 600;
        border-radius: 999px;
        line-height: 1;

      }

      .routeType.router-link-active {
        color: #111;
        background: var(--app-accent);
      }
    }

    .searchHeader {
      width: 100%;
      height: 46px;
      margin-top: 0;
      margin-left: 0;
      line-height: 46px;
      float: none;
      position: relative;

      .input-with-search {
        width: 100%;
        height: 46px;
        position: absolute;
        left: 0;
        top: 0;
        z-index: 10;
        font-size: 16px;
        outline: 0;
        -webkit-appearance: none;
        border: 0;
        border-radius: 999px;
        background-color: #fff;
        border-right-color: var(--app-accent);
        box-sizing: content-box;
        box-shadow: 0 0 0 1px rgba(255, 255, 255, .16) inset, 0 12px 28px rgba(0, 0, 0, .22);

        :deep .el-input-group__prepend {
          box-shadow: none;
          border-radius: 27px 0 0 27px;
          background: transparent;
        }

        :deep .el-input__wrapper {
          box-shadow: none !important;
          background-color: transparent !important;
        }
      }

      .searchBtn {
        width: 90px;
        height: 100%;
        position: absolute;
        right: 0;
        top: 0;
        background: var(--app-accent);
        font-size: 16px;
        text-align: center;
        color: #111;
        border-radius: 0 27px 27px 0;
        z-index: 11;
        letter-spacing: 0;
        cursor: pointer;
        border: 0;
      }
    }

    .rightHeader {
      min-width: 170px;
      height: auto;
      position: relative;
      float: none;
      line-height: 1;
      display: inline-flex;
      align-items: center;
      justify-content: flex-end;
      gap: 10px;

      .box-left {
        height: auto;
        display: inline-flex;
        align-items: center;
        line-height: 1;
        cursor: pointer;
        position: relative;
        margin-left: 0;
        color: rgba(255, 255, 255, .84);

        &:hover {
          color: var(--app-accent);
        }

      }

      .box-right {
        height: auto;
        display: inline-flex;
        align-items: center;
        line-height: 1;
        position: relative;
        margin-left: 0;

        &:hover {
          color: #111;
        }

        .help-link {
          color: #111;
          cursor: pointer;
          display: inline-flex;
          align-items: center;
          justify-content: center;
          min-width: 54px;
          height: 36px;
          padding: 0 14px;
          background: #fff;
          border-radius: 999px;
          text-decoration: none;
          font-weight: 600;
        }
      }
    }
  }
}

.user-trigger {
  height: 38px;
  min-width: 88px;
  max-width: 128px;
  padding: 0 12px 0 7px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: 1px solid rgba(255, 255, 255, .12);
  border-radius: 999px;
  background: rgba(255, 255, 255, .05);
  color: rgba(255, 255, 255, .88);
  font-size: 14px;
  font-weight: 700;
  text-decoration: none;
  cursor: pointer;
  transition: border-color .18s ease, background .18s ease, color .18s ease;

  &:hover {
    color: #111;
    border-color: var(--app-accent);
    background: var(--app-accent);
  }
}

.user-avatar {
  width: 26px;
  height: 26px;
  flex: 0 0 26px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: #111;
  background: #fff7e6;
  box-shadow: inset 0 0 0 2px rgba(245, 158, 11, .28);
}

.user-name {
  min-width: 28px;
  max-width: 54px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.user-arrow {
  flex: 0 0 auto;
}

.loginInfo {
  width: 118px;
  list-style-type: none;
  margin: 0;
  padding: 6px;

  li {
    height: 34px;
    line-height: 34px;

    a {
      width: 100%;
      height: 34px;
      display: block;
      padding: 0 10px;
      border-radius: 8px;
      font-size: 14px;

      &:hover {
        background: var(--app-accent-soft);
        color: #7a3f00;
      }
    }
  }

  .logOut {
    cursor: pointer;

    .loginOut {
      width: 100%;
      height: 34px;
      display: block;
      padding: 0 10px;
      border-radius: 8px;
      font-size: 14px;

      &:hover {
        background: var(--app-accent-soft);
        color: #7a3f00;
      }
    }
  }
}

.login-popover-entry {
  width: 100%;
  height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: var(--app-accent);
  color: #111;
  font-weight: 800;
}


.city {
  width: 626px;
  z-index: 999;
  position: relative;
  left: -115px;
  top: -72px;
  margin-top: 60px;
  background: #FFF;
  border: 1px solid var(--app-border);
  box-shadow: 0 18px 50px rgba(24, 24, 27, .22);
  border-radius: var(--app-radius);
  padding: 21px;
  max-height: 1500px;
  overflow: hidden;

  .now-city {
    line-height: 25px;

    .title-city {
      width: 86px;
      display: inline-block;
      margin-right: 15px;
      vertical-align: top;
      font-size: 16px;
      color: #111;
      letter-spacing: .56px;
      float: left;

    }

    .city-name {
      display: inline-block;
      margin-right: 15px;
      vertical-align: top;
      font-size: 16px;
      color: #111;
      letter-spacing: .56px;
      float: left;

    }

    .select-city {
      color: #111;
      background-color: var(--app-accent);
      padding: 0 10px;
      border-radius: 4px;
    }
  }

  .hot-city {
    line-height: 25px;
    margin-top: 40px;

    .title-city {
      width: 86px;
      display: inline-block;
      margin-right: 15px;
      vertical-align: top;
      font-size: 16px;
      color: #111;
      letter-spacing: .56px;
      float: left;
    }

    .list-city {
      list-style: none;
      width: 525px;
      //display: inline-block;
      line-height: 29px;
      margin-top: -3px;


      .city-name {

        display: inline-block;
        margin-right: 15px;
        vertical-align: top;
        font-size: 16px;
        color: #111;
        letter-spacing: .56px;

        &:hover {
          color: var(--app-accent);
          cursor: pointer;
        }
      }
    }
  }

  .others-city {
    line-height: 25px;
    padding-top: 15px;
    border-top: 1px solid #EEE;
    margin-top: 15px;

    .title-city {
      display: inline-block;
      margin-right: 15px;
      vertical-align: top;
      font-size: 16px;
      color: #111;
      letter-spacing: .56px;
      float: left;
      width: 86px;
    }

    .list-city {
      list-style: none;
      width: 525px;
      //display: inline-block;
      line-height: 29px;
      margin-top: -3px;

      .city-name {
        display: inline-block;
        margin-right: 15px;
        vertical-align: top;
        font-size: 16px;
        color: #111;
        letter-spacing: .56px;
        float: left;

        &:hover {
          color: var(--app-accent);
          cursor: pointer;
        }
      }
    }
  }
}


</style>
