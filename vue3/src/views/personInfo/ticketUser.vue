<template>
  <!--个人信息-->
  <div class="container">
    <Header></Header>
    <main class="profile-page">
    <div class="section">
      <MenuSideBar class="sidebarMenu" activeIndex="4"></MenuSideBar>
      <div class="right-section">
        <div class="breadcrumb">
          <span>账户中心</span>
          <strong>常用购票人管理</strong>
        </div>
        <div class="right-tab">
          <div class="toolbar">
            <div>
              <h2>实名观演人</h2>
              <p>维护常用购票人信息，下单时可直接选择。</p>
            </div>
            <el-button class="addUser" @click="addTicketUser">新增购票人</el-button>
          </div>
          <el-table :data="ticketUserListData" v-if="isShow" style="width: 100%" border>
            <el-table-column  type="index"  label="序号" width="100px"   align="center"/>
            <el-table-column prop="relName" label="姓名"    align="center"/>
            <el-table-column prop="index" label="证件类型"  align="center">
              <template #default="scope">
                {{ getIdTypeName(scope.row.idType)}}
              </template>
            </el-table-column>
            <el-table-column prop="idNumber" label="证件号"   align="center" />
            <el-table-column prop="index" label="操作"   align="center">
              <template #default="scope">
                <el-button link type="primary" icon="Delete" @click="delTicketUser(scope.row.id)">删除</el-button>
              </template>

            </el-table-column>

          </el-table>
          <div class="addTicketUserInfo"  v-if="!isShow">
            <div class="title">新增购票人信息</div>
            <div class="line"></div>
            <el-form ref="ticketRef" :model="formTicket" :rules="formTicketRules" class="ticketForm" label-width="100px" >
              <el-col :span="24">
                <el-form-item label="姓名:"  prop="relName" >
                  <el-input
                      v-model="formTicket.relName"
                      type="text"
                      placeholder="请填填写姓名"
                  > </el-input>
                </el-form-item>
              </el-col>
              <el-col :span="24">
                <el-form-item label="证件类型:" prop="idType"  >
                  <el-select  v-model="formTicket.idType"  >
                    <el-option v-for="item in idType"
                               :value="item.value"
                               :label="item.name" >{{item.name}}</el-option>
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="24">
                <el-form-item label="证件号码:" prop="idNumber" >
                  <el-input
                      v-model="formTicket.idNumber"
                      type="text"
                      placeholder="请填写证件号码"
                  ></el-input>
                </el-form-item>
              </el-col>
             <el-form-item>
               <el-button
                   class="save"
                   @click.prevent="saveTicket"
               >保存</el-button>
               <el-button
                   class="btn"
                   @click.prevent="closeTicket"
               >取消</el-button>
             </el-form-item>
            </el-form>
          </div>
        </div>
      </div>
    </div>
    </main>
    <Footer></Footer>
  </div>

</template>

<script setup name="TicketUser">
import MenuSideBar from '../../components/menuSidebar/index'
import Header from '../../components/header/index'
import Footer from '../../components/footer/index'
import {ref, computed, onMounted, reactive, getCurrentInstance} from 'vue'
import useUserStore from "../../store/modules/user";
import {delTicketUserApi, selectTicketUserListApi} from '@/api/accountCenter.js'
import {getIdTypeName} from '@/api/common.js'
import {ElMessage} from 'element-plus'
import { getUserIdKey} from "@/utils/auth";
import {saveTicketUser} from "@/api/buyTicketUser";


const {proxy} = getCurrentInstance();
const useUser = useUserStore()
//购票人列表入参
const ticketUserListParams = reactive({
  userId:undefined
})
const formTicket = ref({})
formTicket.value.idType = ref('1')

const formTicketRules = ref({
  relName:  [{ required: true, message: "请输入姓名", trigger: "blur" }],
  idNumber:[{ required: true, message: "请输入证件号码", trigger: "blur" }],
})
//购票人列表数据
const ticketUserListData = ref([])
const isShow = ref(true)

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
// 获取路由参数
ticketUserListParams.userId = useUser.userId
selectTicketUserList()
//购票人列表方法
function selectTicketUserList() {
  selectTicketUserListApi(ticketUserListParams).then(response => {
    ticketUserListData.value = response.data;
  })
}

function delTicketUser(ticketUserId){
  const delTicketUserParam = {'id' : ticketUserId}
  delTicketUserApi(delTicketUserParam).then(response => {
    selectTicketUserList()
  })
}
//新增购票人
function addTicketUser(){
  isShow.value =false
  reset()
}



//保存
function saveTicket(){
  proxy.$refs.ticketRef.validate(valid => {
    if (valid) {
      formTicket.value.userId=getUserIdKey()
      saveTicketUser(formTicket.value).then(response=>{
        if(response.code==0){
          isShow.value = true
          selectTicketUserList()
          reset()
        }
      })


    }
  });
}
//取消
function closeTicket(){
  isShow.value =true
  reset()
}

function reset(){
  formTicket.value= {}
  formTicket.value.idType = ref('1')
}
</script>

<style scoped lang="scss">
.container{
  min-height: 100vh;
  background: var(--app-bg);

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
        padding: 22px;
        border: 1px solid var(--app-border);
        border-radius: 8px;
        background: #fff;
        box-shadow: var(--app-shadow);

        .toolbar {
          display: flex;
          align-items: center;
          justify-content: space-between;
          gap: 18px;
          margin-bottom: 18px;

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

          .addUser{
            flex: 0 0 auto;
            background-color: var(--app-accent);
            color: #111;
            border: none;
            font-weight: 800;
            border-radius: 8px;
          }
        .addTicketUserInfo{
          border: 1px solid var(--app-border);
          border-radius: 8px;
          background: var(--app-surface-soft);
          .title{
            line-height: 46px;
            border-bottom: 1px solid var(--app-border);
            padding-left: 20px;
            font-size: 16px;
            color: var(--app-text);
            font-weight: 800;
          }
          .line{
            display: none;
          }
          .ticketForm{
            max-width: 560px;
            margin-top: 0;
            padding: 28px 34px;
            .save{
              background-color: var(--app-primary);
              color: #fff;
              border: none;
              border-radius: 8px;
              font-weight: 800;
            }
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
:deep(.el-select .el-input__wrapper ) {
  flex-grow: 0;
  width: 320px !important;
}

:deep(.el-table) {
  border-radius: 8px;
  overflow: hidden;
  box-shadow: var(--app-shadow);
}

:deep(.el-table th.el-table__cell) {
  background: #111113;
  color: rgba(255, 255, 255, .78);
}

:deep(.el-table--border .el-table__cell) {
  border-color: var(--app-border);
}

:deep(.el-button--primary.is-link) {
  color: var(--app-danger);
}

@media (max-width: 980px) {
  .container .section {
    width: min(100% - 24px, 1180px);
    grid-template-columns: 1fr;

    .sidebarMenu {
      position: static;
    }
  }

  .container .section .right-section .right-tab .toolbar {
    align-items: flex-start;
    flex-direction: column;
  }
}

</style>
