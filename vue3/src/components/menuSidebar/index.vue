<template>
  <div class="app-container">

    <el-row class="sidebar">
      <el-col :span="24">
        <h5 class="mb-2">我的票务</h5>
        <div class="menu_border">
          <el-menu
              :default-openeds="defaultOpened1"
              class="el-menu-vertical-demo"
              @open="handleOpen"
              @close="handleClose"

          >
            <el-sub-menu index="1" class="titleMenu">
              <template #title>
                <span>交易中心</span>
              </template>
              <el-menu-item index="5" :class="{ 'is-active': activeIndex === '5'}"><router-link to="/orderManagement/index">订单管理</router-link></el-menu-item>
            </el-sub-menu>

          </el-menu>
          <el-menu
              :default-openeds="defaultOpened2"
              class="el-menu-vertical-demo"
              @open="handleOpen"
              @close="handleClose"
          >
            <el-sub-menu index="1">
              <template #title>
                <span>账户中心</span>
              </template>
<!--              <el-menu-item index="1" :class="{ 'is-active': props.activeIndex === '1' }">收货地址</el-menu-item>-->
              <el-menu-item index="2" :class="{ 'is-active': props.activeIndex === '2' }"><router-link to="/accountSettings/index">账号设置</router-link></el-menu-item>
              <el-menu-item index="3" :class="{ 'is-active': props.activeIndex === '3' }"><router-link to="/personInfo/index">个人信息</router-link></el-menu-item>
              <el-menu-item index="4" :class="{ 'is-active': props.activeIndex === '4' }"><router-link to="/personInfo/ticketUser">常用购票人</router-link></el-menu-item>
            </el-sub-menu>

          </el-menu>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import {ref, defineProps} from 'vue'
//默认所有项都打开
const defaultOpened1 = ref(['1'])
const defaultOpened2 = ref(['1', '2', '3', '4'])
const props = defineProps({
  activeIndex: String
});
const handleOpen = () => {}
const handleClose = () => {}


</script>

<style scoped lang="scss">
.app-container {
  .sidebar {
    width: 220px;
  }
}

.el-menu--vertical:not(.el-menu--collapse):not(.el-menu--popup-container) .el-menu-item,
.el-menu--vertical:not(.el-menu--collapse):not(.el-menu--popup-container) .el-menu-item-group__title,
.el-menu--vertical:not(.el-menu--collapse):not(.el-menu--popup-container) .el-sub-menu__title {
  padding-left: 0;
}

:deep(.menu_border) {
  padding: 10px;
  border: 1px solid rgba(24, 24, 27, .08);
  border-radius: 0 0 8px 8px;
  overflow: hidden;
  background: #fff;
  box-shadow: 0 16px 38px rgba(24, 24, 27, .08);
}

:deep(.el-menu) {
  border-right: none;
  background: transparent;
}

:deep(.el-sub-menu .el-menu-item) {
  height: 38px;
  line-height: 38px;
  margin: 4px 0;
  padding: 0 !important;
  border-radius: 8px;
  color: var(--app-text-muted);
  transition: background .18s ease, color .18s ease, transform .18s ease;
}

:deep(.el-sub-menu .el-menu-item a){
  width: 100%;
  height: 100%;
  display: block;
  padding: 0 14px 0 32px;
  color: inherit;
  text-decoration: none;
}

:deep(.el-sub-menu .el-menu-item:hover),
:deep(.el-sub-menu .el-menu-item.is-active) {
  border-left: none;
  background: var(--app-accent-soft);
  text-decoration: none;
  color: #7a3f00;
  transform: translateX(2px);
}

:deep(.el-sub-menu .el-menu-item.is-active) {
  font-weight: 800;
  box-shadow: inset 3px 0 0 var(--app-accent);
}

:deep(.el-menu .el-sub-menu .el-sub-menu__title) {
  height: 36px;
  line-height: 36px;
  padding: 0 14px !important;
  margin: 8px 0 4px;
  border-radius: 8px;
  background: var(--app-primary-soft);
  font-family: Arial, "Microsoft YaHei", sans-serif;
  color: var(--app-text);
  font-weight: 700;
  font-size: 13px;
}

/* 隐藏Element Plus侧边菜单展开/折叠箭头 */
:deep(.el-sub-menu .el-sub-menu__icon-arrow) {
  display: none;
}

:deep(.el-submenu__title .el-submenu__icon-arrow) {
  display: none;
}

.mb-2 {
  width: 220px;
  height: 54px;
  position: relative;
  margin: 0;
  padding-left: 18px;
  line-height: 54px;
  border-radius: 8px 8px 0 0;
  background:
    linear-gradient(135deg, rgba(245, 158, 11, .18), transparent 42%),
    var(--app-primary);
  font-family: Arial, "Microsoft YaHei", sans-serif;
  font-size: 15px;
  color: #fff;
  font-weight: 700;
  border-bottom: 4px solid var(--app-accent);
}

</style>
