<template>
  <!--详情-->
  <Header></Header>
  <div class="app-container">
    <div class="goods">共 <span>{{ total || goods }}</span> 个项目</div>
    <div class="box-main">
      <div class="box-main-left">
        <div class="box-tLeft">
          <div class="box-type">
            <el-collapse v-model="activeNames" @change="handleChange">
              <el-collapse-item name="1">
                <template #title>
                  <span class="title">城市</span>
                </template>
                <div class="city">
                  <div class="current-filter">
                    <span>当前城市</span>
                    <strong>{{ currentCity || '全部' }}</strong>
                  </div>
                  <ul>
                    <li v-show="isShow" v-for="(item,index) in cityArr.slice(0,22)" :key="item.id"
                        @click="cityClick(item,index)"
                    >
                      <span v-if="item.name==currentCity" class="active">{{ item.name }}</span>
                      <span v-else>{{ item.name }}</span>
                    </li>
                    <li v-show="!isShow" v-for="(item,index) in cityArr" :key="item.id"
                        @click="cityClick(item,index)" :class="{active: activeCityIndex == index}">{{ item.name }}
                    </li>

                  </ul>
                </div>
                <div class="btn" v-show="cityArr.length>22">
                  <span v-show="isShow" @click="isShow=false">更多</span>
                  <span v-show="!isShow" @click="isShow=true">收起</span>
                </div>
              </el-collapse-item>
              <el-collapse-item name="2">
                <template #title>
                  <span class="title">分类:</span>
                </template>
                <div>
                  <ul>
                    <li v-for="(item,ind) in categoryArr" :key="item.id"
                        @click="categoryClick(item,ind)"
                    >
                      <span v-if="$route.query.name==item.name" class="active">{{ item.name }}</span>
                      <span v-if="$route.query.name!=item.name&&item.name=='全部'" :class="{active:isActive}">全部</span>
                      <span v-if="$route.query.name!=item.name&&item.name!='全部'" :class="{active: activeIndex == ind}">{{
                          item.name
                        }}</span>
                    </li>
                  </ul>
                </div>
              </el-collapse-item>
              <el-collapse-item name="3" v-if="isShowChildren">
                <template #title>
                  <span class="title">子类:</span>
                </template>
                <div>
                  <ul>
                    <li v-for="(item,index) in childrenArr" :key="item.id"
                        @click="childrenClick(item,index)" :class="{active: activeChildrenIndex == index}">
                      {{ item.name }}
                    </li>

                  </ul>
                </div>
              </el-collapse-item>

              <el-collapse-item name="4">
                <template #title>
                  <span class="title">时间:</span>
                </template>
                <div>
                  <ul>
                    <li v-for="(item,index) in timeArr" :key="item.id"
                        @click="timeClick(item,index)" :class="{active: activeTimeIndex == index}">
                      <span>{{ item.name }}</span>
                    </li>
                    <li class="liDate">
                      <el-date-picker
                          v-if="isShowDate"
                          v-model="value1"
                          type="daterange"
                          start-placeholder="开始时间"
                          end-placeholder="结束时间"
                          @change="handleChangeDate"
                      />
                    </li>
                  </ul>
                </div>

              </el-collapse-item>

            </el-collapse>
          </div>
          <div class="box-sort">
            <el-tabs type="border-card" class="box-tabs" @tab-click="handleClickTab">
              <el-tab-pane label="Config">
                <template #label>相关度排序</template>
                <ul>
                  <li v-for="item in cardArr">
                    <router-link :to="{name:'detial',params:{id:item.id}}" class="link">
                      <img :src="item.itemPicture" alt="">
                    </router-link>
                    <div class="item-txt">
                      <div class="item-title">
                        <span>【{{ item.areaName }}】</span>
                        <router-link :to="{name:'detial', params:{id:item.id}}" class="link-detial" v-if="titleIsShow">
                          {{ item.title }}
                        </router-link>
                        <router-link :to="{name:'detial', params:{id:item.id}}" class="link-detial" v-if="!titleIsShow"
                                     v-html="item.title"></router-link>

                      </div>
                      <div class="item-content" v-if="titleIsShow">艺人：{{ item.actor }}</div>
                      <div class="item-content" v-if="!titleIsShow"><span>艺人：</span><span  v-html="item.actor"></span> </div>

                      <div class="item-content"> {{ item.areaName }} | {{ item.place }}</div>
                      <div class="item-content">{{ formatDateWithWeekday(item.showTime, item.showWeekTime) }}</div>

                      <div class="item-tag"></div>
                      <div class="item-price">
                        <span class="price">{{ item.minPrice }}-{{ item.maxPrice }}元</span>
                        <span class="item-content">售票中</span></div>
                    </div>
                  </li>
                </ul>
                <pagination
                    v-show="total > 0"
                    :total="total"
                    v-model:page="queryParams.pageNum"
                    v-model:limit="queryParams.pageSize"
                    @pagination="getList"
                />
              </el-tab-pane>
              <el-tab-pane label="Config">
                <template #label>推荐排序</template>
                <ul>
                  <li v-for="item in cardArr">
                    <router-link :to="{name:'detial',params:{id:item.id}}" class="link">
                      <img :src="item.itemPicture" alt="">
                    </router-link>
                    <div class="item-txt">
                      <div class="item-title">
                        <span>【{{ item.areaName }}】</span>
                        <router-link :to="{name:'detial', params:{id:item.id}}" class="link-detial" v-if="titleIsShow">
                          {{ item.title }}
                        </router-link>
                        <router-link :to="{name:'detial', params:{id:item.id}}" class="link-detial" v-if="!titleIsShow"
                                     v-html="item.title"></router-link>

                      </div>
                      <div class="item-content" v-if="titleIsShow">艺人：{{ item.actor }}</div>
                      <div class="item-content" v-if="!titleIsShow"><span>艺人：</span><span  v-html="item.actor"></span> </div>
                      <div class="item-content"> {{ item.areaName }} | {{ item.place }}</div>
                      <div class="item-content">{{ formatDateWithWeekday(item.showTime, item.showWeekTime) }}</div>

                      <div class="item-tag"></div>
                      <div class="item-price">
                        <span class="price">{{ item.minPrice }}-{{ item.maxPrice }}元</span>
                        <span class="item-content">售票中</span></div>
                    </div>
                  </li>
                </ul>
                <pagination
                    v-show="total > 0"
                    :total="total"
                    v-model:page="queryParams.pageNum"
                    v-model:limit="queryParams.pageSize"
                    @pagination="getList"
                />
              </el-tab-pane>
              <el-tab-pane label="Role">
                <template #label>最近开场</template>
                <ul>
                  <li v-for="item in cardArr">
                    <router-link :to="{name:'detial',params:{id:item.id}}" class="link">
                      <img :src="item.itemPicture" alt="">
                    </router-link>
                    <div class="item-txt">
                      <div class="item-title">
                        <span>【{{ item.areaName }}】</span>
                        <router-link :to="{name:'detial', params:{id:item.id}}" class="link-detial" v-if="titleIsShow">
                          {{ item.title }}
                        </router-link>
                        <router-link :to="{name:'detial', params:{id:item.id}}" class="link-detial" v-if="!titleIsShow"
                                     v-html="item.title"></router-link>

                      </div>
                      <div class="item-content" v-if="titleIsShow">艺人：{{ item.actor }}</div>
                      <div class="item-content" v-if="!titleIsShow"><span>艺人：</span><span  v-html="item.actor"></span> </div>
                      <div class="item-content"> {{ item.areaName }} | {{ item.place }}</div>
                      <div class="item-content">{{ formatDateWithWeekday(item.showTime, item.showWeekTime) }}</div>

                      <div class="item-tag"></div>
                      <div class="item-price">
                        <span class="price">{{ item.minPrice }}-{{ item.maxPrice }}元</span>
                        <span class="item-content">售票中</span></div>
                    </div>
                  </li>
                </ul>
                <pagination
                    v-show="total > 0"
                    :total="total"
                    v-model:page="queryParams.pageNum"
                    v-model:limit="queryParams.pageSize"
                    @pagination="getList"
                />
              </el-tab-pane>
              <el-tab-pane label="Task">
                <template #label>最新上架</template>
                <ul>
                  <li v-for="item in cardArr">
                    <router-link :to="{name:'detial',params:{id:item.id}}" class="link">
                      <img :src="item.itemPicture" alt="">
                    </router-link>
                    <div class="item-txt">
                      <div class="item-title">
                        <span>【{{ item.areaName }}】</span>
                        <router-link :to="{name:'detial', params:{id:item.id}}" class="link-detial" v-if="titleIsShow">
                          {{ item.title }}
                        </router-link>
                        <router-link :to="{name:'detial', params:{id:item.id}}" class="link-detial" v-if="!titleIsShow"
                                     v-html="item.title"></router-link>

                      </div>
                      <div class="item-content" v-if="titleIsShow">艺人：{{ item.actor }}</div>
                      <div class="item-content" v-if="!titleIsShow"><span>艺人：</span><span  v-html="item.actor"></span> </div>
                      <div class="item-content"> {{ item.areaName }} | {{ item.place }}</div>
                      <div class="item-content">{{ formatDateWithWeekday(item.showTime, item.showWeekTime) }}</div>

                      <div class="item-tag"></div>
                      <div class="item-price">
                        <span class="price">{{ item.minPrice }}-{{ item.maxPrice }}元</span>
                        <span class="item-content">售票中</span></div>
                    </div>
                  </li>
                </ul>
                <pagination
                    v-show="total > 0"
                    :total="total"
                    v-model:page="queryParams.pageNum"
                    v-model:limit="queryParams.pageSize"
                    @pagination="getList"
                />
              </el-tab-pane>
            </el-tabs>

          </div>
        </div>
      </div>
      <div class="box-main-right">
        <div class="box-like">
          您可能还喜欢
        </div>
        <ul class="search__box">
          <li class="search__item" v-for="item in recommendList">
            <router-link :to="{name:'detial',params:{id:item.id}}" class="link">
              <img :src="item.itemPicture" alt="">
            </router-link>
            <div class="search_item_info">
              <div class="recommend-meta">
                <span>{{ item.areaName || currentCity }}</span>
                <span v-if="item.programCategoryName">{{ item.programCategoryName }}</span>
              </div>
              <router-link :to="{name:'detial',params:{id:item.id}}" class="link__title">
                {{ item.title }}
              </router-link>
              <div class="search__item__info__venue">{{ item.place }}</div>
              <div class="search__item__info__venue">{{ formatDateWithWeekday(item.showTime, item.showWeekTime) }}</div>
              <div class="search__item__info__price">
                <strong v-if="item.minPrice && item.maxPrice">{{ item.minPrice }}-{{ item.maxPrice }}元</strong>
                <strong v-else-if="item.minPrice">{{ item.minPrice }}元起</strong>
                <strong v-else>售票中</strong>
              </div>
            </div>
          </li>
        </ul>
        <div class="recommend-empty" v-if="!recommendList.length">
          <span>暂无推荐</span>
          <p>切换城市或分类后再看看。</p>
        </div>
      </div>
    </div>
    <Footer></Footer>
  </div>
</template>

<script setup>
//引入reactive
import {getCurrentInstance, onMounted, reactive, ref} from 'vue'
import {getCurrentCity, getOtherCity} from '@/api/area'
import {getcategoryType} from "@/api/index";
import {getCurrentDate, useMitt,formatDateWithWeekday} from "@/utils/index";
import {getChildrenType, getProgramPageType} from "@/api/allType";
import {getProgramRecommendList} from "@/api/recommendlist.js"
//引入路由器
import {useRouter} from 'vue-router'

const router = useRouter()

const emitter = useMitt();

const goods = ref(5)

let count = 0;

const cityArr = ref([])
const categoryArr = ref([])
const childrenArr = ref([])
const currentCity = ref('')
const parentProgramCategoryId = ref('')
const isShow = ref(true)
const activeIndex = ref('')
const isShowClass = ref('')
const activeCityIndex = ref('')
const activeChildrenIndex = ref('')
const activeTimeIndex = ref('')
const isShowChildren = ref(false)
const queryParams = ref({pageNum: 1, pageSize: 10})
const total = ref(0)
const isShowDate = ref(false)
const value1 = ref([])
const timeType = ref(0)
const cardArr = ref([])
const titleIsShow = ref(true)
const rawHtml = ref('')
//推荐列表数据
const recommendList = ref([])
const isActive = ref(false)
const pageParams = ref({
  areaId: undefined,
  endDateTime: undefined,
  pageNumber: undefined,
  pageSize: undefined,
  parentProgramCategoryId: undefined,
  programCategoryId: undefined,
  startDateTime: undefined,
  timeType: undefined,
  type: 1//1:相关度排序(默认) 2:推荐排序 3:最近开场 4:最新上架
})
//推荐节目列表入参
const recommendParams = reactive({
  areaId: undefined,
  parentProgramCategoryId: 1,
  programId: undefined
})
const {proxy} = getCurrentInstance();

//手风琴面板
const activeNames = ref(['1', '2', '3', '4'])
const handleChange = (val) => {
  activeNames.value = ['1', '2', '3', '4'];//始终让四个面板显示，不可关闭
}


//获取城市数据
const getcityList = () => {
  getOtherCity().then(response => {
    cityArr.value = response.data
    cityArr.value.unshift({name: '全部', id: ''})
  })
}
getcityList()


//当前城市
const getCurrent = () => {
  getCurrentCity().then(response => {
    let {name, parentId, id, type} = response.data
    currentCity.value = name

  })

}
getCurrent()

//获取分类
const getTypeList = () => {
  getcategoryType({type: 1}).then(response => {
    categoryArr.value = response.data
    categoryArr.value.unshift({name: '全部', id: ''})
  })
}

getTypeList()
//获取子类
const getChildrenTypeList = () => {
  getChildrenType({parentProgramCategoryId: parentProgramCategoryId.value}).then(response => {
    childrenArr.value = response.data
    childrenArr.value.unshift({name: '全部', id: ''})
    if (childrenArr.value == '') {
      isShowChildren.value = false
    }
  })
}

//分类

//点击分类每一项
const categoryClick = (item, ind) => {
  proxy.$route.query.name = ''
  activeIndex.value = ind
  const categoryId = item.id === '' ? undefined : item.id
  parentProgramCategoryId.value = categoryId
  pageParams.value.parentProgramCategoryId = categoryId
  recommendParams.parentProgramCategoryId = categoryId;
  if (item.name == '全部') {
    isActive.value = true
  } else {
    isActive.value = false
  }
  if (item.id == '') {
    isShowChildren.value = false
    pageParams.value.programCategoryId = undefined
    activeChildrenIndex.value = ''
    getList()
    getRecommendList()
  } else {
    isShowChildren.value = true
    if (isActive.value == false) {
        getChildrenTypeList()
    }
    getList()
    getRecommendList()
  }

}
//点击城市
const cityClick = (item, index) => {
  activeCityIndex.value = index
  currentCity.value = item.name
  pageParams.value.areaId = item.id === '' ? undefined : item.id
  //推荐节目列表入参中的区域字段
  recommendParams.areaId = item.id === '' ? undefined : item.id
  getList()
  getRecommendList()
}
//点击子类
const childrenClick = (item, index) => {
  activeChildrenIndex.value = index
  pageParams.value.programCategoryId = item.id
  getList()
}
//点击时间
const timeClick = (item, index) => {
  activeTimeIndex.value = index
  timeType.value = item.id
  pageParams.value.timeType = item.id
  if (item.id == 5) {
    isShowDate.value = true
    pageParams.value.timeType = 5
  } else {
    isShowDate.value = false
    pageParams.value.startDateTime = undefined
    pageParams.value.endDateTime = undefined
    getList()
  }

}
const handleChangeDate = (selection) => {
  pageParams.value.startDateTime = getCurrentDate(selection[0])
  pageParams.value.endDateTime = getCurrentDate(selection[1])
  getList()
}

//时间数组
const timeArr = ref(
    [{
      name: '全部',
      id: 0
    }, {
      name: '今天',
      id: 1
    },
      {
        name: '明天',
        id: 2
      },
      {
        name: '本周末',
        id: 3
      },
      {
        name: '一个月内',
        id: 4
      }, {
      name: '按日历',
      id: 5
    },
    ])


const getList = () => {
  pageParams.value.timeType = timeType.value
  pageParams.value.pageNumber = queryParams.value.pageNum
  pageParams.value.pageSize = queryParams.value.pageSize
  getProgramPageType(pageParams.value).then(response => {
    cardArr.value = response.data.list
    total.value = Number(response.data.totalSize)
  })
}

//节目推荐列表
const getRecommendList = () => {
  //如果没有选择父类型，则默认为演唱会
  if (recommendParams.parentProgramCategoryId == null || recommendParams.parentProgramCategoryId === '') {
    recommendParams.parentProgramCategoryId = 1
  }
  getProgramRecommendList(recommendParams).then(response => {
    recommendList.value = (response.data || []).slice(0, 6);
  })
}

onMounted(() => {
  pageParams.value.pageNumber = 1
  pageParams.value.pageSize = 10
  pageParams.value.timeType = timeType.value
  pageParams.value.parentProgramCategoryId = proxy.$route.query.id || undefined
  recommendParams.parentProgramCategoryId = proxy.$route.query.id || recommendParams.parentProgramCategoryId
  getList()
  getRecommendList()
  emitter.on('searchList', (data) => {
    cardArr.value = data.list
    total.value = Number(data.totalSize)
    titleIsShow.value = false

  })
})

function handleClickTab(tab, event) {
  pageParams.value.type = Number(tab.index) + 1
  getList()
}

function removeTag(str, tag) {
  const regex = new RegExp(`<${tag}[^>]*>|</${tag}>`, 'gi');
  return str.replace(regex, '');
}


</script>

<style scoped lang="scss">
.app-container {
  width: min(1320px, calc(100vw - 64px));
  min-height: calc(100vh - 86px);
  margin: 0 auto;
  padding: 26px 0 46px;
}

.goods {
  display: inline-flex;
  align-items: center;
  height: 34px;
  margin-bottom: 14px;
  padding: 0 14px;
  color: rgba(255, 255, 255, .78);
  background: #111113;
  border-radius: 999px;
  font-size: 14px;
  box-shadow: 0 12px 30px rgba(24, 24, 27, .14);

  span {
    color: var(--app-accent);
    font-weight: 800;
  }
}

.box-main {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 16px;
  align-items: start;
}

.box-main-left,
.box-tLeft {
  min-width: 0;
}

.box-type {
  padding: 18px 26px;
  border: 1px solid rgba(24, 24, 27, .16);
  background:
    radial-gradient(circle at 12% 8%, rgba(245, 158, 11, .20), transparent 25%),
    linear-gradient(135deg, #111113, #202026);
  border-radius: 8px;
  box-shadow: 0 18px 44px rgba(24, 24, 27, .16);
}

.box-type ul {
  margin: 0;
  padding: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
}

.box-type li {
  list-style: none;
  min-height: 28px;
  display: inline-flex;
  align-items: center;
  padding: 0 9px;
  color: rgba(255, 255, 255, .72);
  border: 1px solid transparent;
  border-radius: 999px;
  white-space: nowrap;
  cursor: pointer;
  transition: color .18s ease, background .18s ease, border-color .18s ease;

  &:hover {
    color: #111;
    background: var(--app-accent);
  }
}

.box-type .city {
  min-width: 0;
}

.current-filter {
  min-height: 30px;
  margin-bottom: 8px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 0 10px;
  border: 1px solid rgba(245, 158, 11, .28);
  border-radius: 999px;
  background: rgba(245, 158, 11, .08);

  span {
    color: rgba(255, 255, 255, .52);
    font-size: 12px;
  }

  strong {
    color: var(--app-accent);
    font-size: 13px;
  }
}

.box-type .btn {
  margin-top: 10px;
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  color: var(--app-accent);
  border: 1px solid rgba(245, 158, 11, .30);
  border-radius: 999px;
  cursor: pointer;
}

.box-type .liDate {
  padding: 0 !important;
}

.active {
  min-height: 28px;
  display: inline-flex;
  align-items: center;
  padding: 0 10px;
  background-color: var(--app-accent);
  color: #111 !important;
  border-radius: 999px;
  white-space: nowrap;
  cursor: pointer;
  font-weight: 800;
}

.box-sort {
  margin-top: 12px;
}

.box-tabs ul {
  margin: 0;
  padding: 0;
}

.box-tabs li {
  list-style-type: none;
  display: grid;
  grid-template-columns: 154px minmax(0, 1fr);
  gap: 20px;
  min-height: 216px;
  padding: 22px 24px;
  border-bottom: 1px solid var(--app-border);
  background: #fff;
  transition: background .18s ease, transform .18s ease, box-shadow .18s ease;

  &:hover {
    background: linear-gradient(90deg, var(--app-accent-soft), #fff 60%);
    transform: translateY(-1px);
  }
}

.box-tabs li:last-child {
  border-bottom: none;
}

.box-tabs .link {
  width: 154px;
  height: 206px;
  display: block;
  overflow: hidden;
  border-radius: 8px;
  box-shadow: 0 14px 30px rgba(24, 24, 27, .14);
  text-decoration: none;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform .25s ease;
  }

  &:hover img {
    transform: scale(1.04);
  }
}

.item-txt {
  min-width: 0;
  display: flex;
  flex-direction: column;
  padding: 2px 0;
  line-height: 1.6;
}

.item-title {
  margin-bottom: 10px;
  color: var(--app-text);
  font-size: 18px;
  font-weight: 800;
  line-height: 1.45;
}

.link-detial,
.link__title {
  color: inherit;
  text-decoration: none;
  outline: 0;

  &:hover {
    color: var(--app-danger);
  }
}

.item-content {
  margin-bottom: 5px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: var(--app-text-muted);
  font-size: 14px;
}

.item-price {
  margin-top: auto;
  display: flex;
  align-items: baseline;
  gap: 16px;
  color: var(--app-text-muted);
}

.price {
  color: var(--app-danger);
  font-size: 18px;
  font-weight: 900;
}

.box-main-right {
  position: sticky;
  top: 96px;
  border: 1px solid var(--app-border);
  border-radius: 8px;
  background: var(--app-surface);
  box-shadow: var(--app-shadow);
  overflow: hidden;
}

.box-like {
  min-height: 44px;
  display: flex;
  align-items: center;
  padding: 0 16px;
  background-color: #111113;
  border-bottom: 3px solid var(--app-accent);
  font-size: 14px;
  color: #fff;
  font-weight: 800;
}

.search__box {
  margin: 0;
  padding: 10px;
}

.search__item {
  display: grid;
  grid-template-columns: 86px minmax(0, 1fr);
  gap: 12px;
  padding: 12px;
  border-radius: 8px;
  background: var(--app-surface);
  border: 1px solid transparent;
  transition: background .18s ease, transform .18s ease;

  &:hover {
    background: var(--app-primary-soft);
    border-color: var(--app-border);
    transform: translateY(-1px);
  }

  img {
    width: 86px;
    height: 116px;
    border-radius: 8px;
    object-fit: cover;
  }
}

.search_item_info {
  min-width: 0;
}

.recommend-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin-bottom: 6px;

  span {
    max-width: 88px;
    min-height: 20px;
    display: inline-flex;
    align-items: center;
    padding: 0 7px;
    overflow: hidden;
    border-radius: 999px;
    background: var(--app-accent-soft);
    color: #7a3f00;
    font-size: 12px;
    font-weight: 800;
    white-space: nowrap;
    text-overflow: ellipsis;
  }
}

.link__title {
  display: -webkit-box;
  overflow: hidden;
  color: var(--app-text);
  font-size: 13px;
  font-weight: 800;
  line-height: 1.45;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.search__item__info__venue {
  margin-top: 5px;
  color: var(--app-text-muted);
  font-size: 12px;
  line-height: 1.4;
}

.search__item__info__price {
  margin-top: 6px;
  color: var(--app-text-muted);
  font-size: 12px;

  strong {
    color: var(--app-danger);
  }
}

.recommend-empty {
  padding: 38px 20px;
  text-align: center;
  color: var(--app-text-muted);

  span {
    display: block;
    color: var(--app-text);
    font-weight: 800;
  }

  p {
    margin: 6px 0 0;
    font-size: 12px;
  }
}

:deep(.el-icon svg) {
  display: none;
}

:deep(.el-collapse) {
  --el-collapse-border-color: transparent;
  background: transparent;
  border: none;
}

:deep(.el-collapse-item) {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: 18px;
  padding: 16px 0;
  border-bottom: 1px solid rgba(255, 255, 255, .12);
}

:deep(.el-collapse-item:last-child) {
  border-bottom: none;
}

:deep(.el-collapse-item__header) {
  min-height: 30px;
  height: auto;
  align-items: center;
  background: transparent;
  color: rgba(255, 255, 255, .72);
  border: none;
  line-height: 1;
  white-space: nowrap;
}

:deep(.el-collapse-item__header .title) {
  min-width: 0;
  margin-right: 0;
  color: rgba(255, 255, 255, .52);
  font-weight: 800;
}

:deep(.el-collapse-item__wrap) {
  grid-column: 2;
  background: transparent;
  border: none;
}

:deep(.el-collapse-item__content) {
  padding-bottom: 0;
  color: rgba(255, 255, 255, .72);
}

:deep(.el-date-editor.el-input, .el-date-editor.el-input__wrapper) {
  display: none;
}

:deep(em) {
  color: var(--app-danger);
  font-weight: 900;
}

:deep(.el-tabs--border-card) {
  border: 1px solid var(--app-border);
  border-radius: 8px;
  overflow: hidden;
  box-shadow: var(--app-shadow);
}

:deep(.el-tabs--border-card > .el-tabs__header) {
  background: #111113;
  border-bottom: 3px solid var(--app-accent);
}

:deep(.el-tabs--border-card > .el-tabs__header .el-tabs__item) {
  height: 42px;
  color: rgba(255, 255, 255, .74);
  border: none;
  font-weight: 700;
}

:deep(.el-tabs--border-card > .el-tabs__header .el-tabs__item.is-active) {
  color: #111;
  background: var(--app-accent);
}

@media (max-width: 1120px) {
  .box-main {
    grid-template-columns: 1fr;
  }

  .box-main-right {
    position: static;
  }

  .search__box {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(230px, 1fr));
    gap: 8px;
  }
}

@media (max-width: 760px) {
  .app-container {
    width: min(100% - 24px, 1320px);
    padding-top: 18px;
  }

  :deep(.el-collapse-item) {
    grid-template-columns: 1fr;
    gap: 8px;
  }

  :deep(.el-collapse-item__wrap) {
    grid-column: 1;
  }

  .box-tabs li {
    grid-template-columns: 108px minmax(0, 1fr);
    gap: 14px;
    padding: 16px;
  }

  .box-tabs .link {
    width: 108px;
    height: 146px;
  }

  .item-title {
    font-size: 15px;
  }
}
</style>
