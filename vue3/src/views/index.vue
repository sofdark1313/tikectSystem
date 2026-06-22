<template>
  <Header @updateValue="handleUpdate"></Header>
  <div class="app-container">
    <!--    轮播图-->
    <el-carousel :interval="5000" arrow="always" class="carousel-lamp" height="320px">
      <el-carousel-item v-for="item in picArr" :key="item">
        <img :src="item" alt="票务活动推荐">
      </el-carousel-item>
    </el-carousel>
    <!--    中间各类型-->
    <div class="category">
      <ul>
        <li v-for="(item,ind) in categoryArr">
          <router-link   :to="{ path: '/allType/index', query: {type:item.type,name:item.name,id:item.id} }">
            <i :class="['sprit','sprit'+(ind+1)]"></i>
            <span>{{ item.name }}</span>
          </router-link>
        </li>
      </ul>
    </div>

    <div class="diffrentType" v-for="(item,index) in  programList">
      <div>
        <div class="name">
          <span>{{ item.categoryName }}</span>
          <router-link :to="{ path: '/allType/index', query: {type:1,name:item.categoryName,id:item.categoryId} }" class="more">查看全部</router-link>
        </div>
        <div class="box" v-if="item.programListVoList && item.programListVoList.length">
          <div class="box-left">
            <router-link :to="{ name: 'detial', params: { id: item.programListVoList[0].id }}"><img :src="item.programListVoList[0].itemPicture" alt=""></router-link>
          </div>

            <div class="box-right">
              <div class="rtLink" v-for="(dict,ind) in item.programListVoList.slice(1)">
                <router-link  :to="{ name: 'detial', params: { id: dict.id }}" >
                <img :src="dict.itemPicture" alt="">
                <div class="info">
                  <div class="img-title">{{ dict.title }}</div>
                  <div class="local">{{ dict.place }}</div>
                  <div class="showTime">{{ dict.showTime }}{{ dict.showWeekTime }}</div>
                  <div class="price">{{ dict.minPrice }} <span class="rise">起</span></div>
                </div>
                </router-link>
              </div>
            </div>

        </div>

      </div>
    </div>
    <Footer></Footer>
  </div>
</template>

<script setup>
import Header from '@/components/header/index'
import swiperPic1 from '@/assets/section/ticket-hero-generated.jpg'
import {onMounted, ref} from 'vue'
import Footer from '@/components/footer/index'
import {getcategoryType, getMainCategory} from '@/api/index'
//轮播图目前固定一张
const picArr = [swiperPic1]

const categoryArr = ref([])
const programList = ref([])
const queryParams = ref({
  "areaId": 0,
  "parentProgramCategoryIds": []
})
const programListVoList = ref([])
//     [
//   {name: '演唱会', setClass: 'sprit1', url: '/allType/index'},
//   {name: '话剧歌剧', setClass: 'sprit2', url: '/allType/index'},
//   {name: '体育', setClass: 'sprit3', url: '/allType/index'},
//   {name: '儿童亲子', setClass: 'sprit4', url: '/allType/index'},
//   {name: '展览休闲', setClass: 'sprit5', url: '/allType/index'},
//   {name: '音乐会', setClass: 'sprit6', url: '/allType/index'},
//   {name: '曲苑杂坛', setClass: 'sprit7', url: '/allType/index'},
//   {name: '舞蹈芭蕾', setClass: 'sprit8', url: '/allType/index'},
//   {name: '二次元', setClass: 'sprit9', url: '/allType/index'},
//   {name: '旅游展览', setClass: 'sprit10', url: '/allType/index'}
// ]
//获取中间的类目信息
onMounted(() => {
  getgetcategoryList()

})

//查询节目类型
function getgetcategoryList() {
  getcategoryType({type: 1}).then(response => {
    categoryArr.value = response.data
    getMainCategoryList()
  })
}

function handleUpdate(value) {
  queryParams.value.areaId = value
}

function getMainCategoryList() {
  for (let i = 0; i < 4 && i < categoryArr.value.length; i++) {
    queryParams.value.parentProgramCategoryIds.push(categoryArr.value[i].id);
  }
  getMainCategory(queryParams.value).then(response => {
    programList.value = response.data
  })
}



</script>
<style scoped lang="scss">
.app-container {
  width: min(1440px, calc(100vw - 64px));
  margin: 0 auto;
  padding: 28px 0 44px;

  .carousel-lamp {
    width: 100%;
    overflow: hidden;
    position: relative;
    border-radius: 8px;
    box-shadow: 0 24px 60px rgba(24, 24, 27, .22);
    background: #111113;
    border: 1px solid rgba(24, 24, 27, .16);

    &::after {
      content: "";
      position: absolute;
      left: 0;
      right: 0;
      bottom: 0;
      height: 7px;
      background: linear-gradient(90deg, var(--app-accent), var(--app-danger), var(--app-info));
      z-index: 2;
    }

    img{
      width: 100%;
      height: 320px;
      display: block;
      object-fit: cover;
    }
  }

  .category {

    margin-top: 20px !important;
    padding: 22px 24px 24px;
    border: 1px solid rgba(24, 24, 27, .16);
    border-radius: 8px;
    background: #111113;
    box-shadow: 0 18px 44px rgba(24, 24, 27, .14);
    zoom: 1;

    ul {
      display: grid;
      grid-template-columns: repeat(10, 1fr);
      list-style-type: none;
      margin: 0;
      padding: 0;
      width: 100%;
      height: 80px;

      li {
        float: none;
        display: block;
        width: auto;
        text-align: center;
        transition: transform .2s ease;

        &:hover {
          transform: translateY(-3px);
        }

        a {
          width: 100%;
          height: 50px;
          display: block;


          span {
            width: 100%;
            height: 20px;
            display: inline-block;
            font-size: 16px;
            color: rgba(255, 255, 255, .82);
            text-align: center;
            font-weight: 600;

            &:hover {
              color: var(--app-accent);
            }
          }
        }


        .sprit {
          display: block;
          width: 48px;
          height: 48px;
          margin: 0 auto;
          background: url("/src/assets/section/sprit.png") no-repeat;
          background-size: 100% auto;
          filter: grayscale(1) brightness(2.2) sepia(.8) saturate(3) hue-rotate(350deg);
        }

        .sprit1 {
          background-position: 0 0;
        }

        .sprit2 {
          background-position: 0 -64px;
        }

        .sprit3 {
          background-position: 0 -120px;
        }

        .sprit4 {
          background-position: 0 -180px;
        }

        .sprit5 {
          background-position: 0 -240px;
        }

        .sprit6 {
          background-position: 0 -297px
        }

        .sprit7 {
          background-position: 0 -360px;
        }

        .sprit8 {
          background-position: 0 -420px;
        }

        .sprit9 {
          background-position: 0 -480px;
        }

        .sprit10 {
          background-position: 0 -540px;
        }
      }
    }


  }

  .diffrentType {
    width: 100%;
    position: relative;
    padding: 24px;
    border: 1px solid var(--app-border);
    border-radius: 8px;
    margin-top: 22px;
    display: flex;
    background: var(--app-surface);
    box-shadow: var(--app-shadow);
    overflow: hidden;

    &::before {
      content: "";
      position: absolute;
      left: 0;
      top: 0;
      width: 7px;
      height: 100%;
      background: linear-gradient(180deg, var(--app-accent), var(--app-danger));
    }

    .name {
      font-size: 24px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-left: 5px;
      color: var(--app-text);
      width: 100%;
      height: 40px;
      line-height: 40px;
      overflow: hidden;
      font-weight: 800;
    }

    .more {
      flex: 0 0 auto;
      font-size: 14px;
      color: #111;
      background: var(--app-accent);
      border-radius: 999px;
      padding: 0 14px;
      max-width: 100px;
      line-height: 30px;
      height: 30px;
      margin-top: 5px;
      overflow: hidden;
      text-align: center;
      &:hover {
        color: #111;
        filter: brightness(.95);
      }
    }
  }

  .box {
    margin-top: 15px;
    display: grid;
    grid-template-columns: 270px minmax(0, 1fr);
    gap: 24px;
    align-items: stretch;

    .box-left {
      display: block;
      width: 270px;
      height: 360px;
      position: relative;
      overflow: hidden;
      border: 1px solid rgba(24, 24, 27, .12);
      border-radius: 8px;
      box-shadow: 0 18px 38px rgba(24, 24, 27, .18);

      img {
        width: 100%;
        height: 100%;
        position: absolute;
        left: 0;
        top: 0;
        object-fit: cover;
        transition: transform .25s ease;

        &:hover {
          transform: scale(1.04);
        }

      }
    }

    .box-right {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 22px;
      width: 100%;
      margin-left: 0;
      height: 360px;
      overflow: hidden;

      .rtLink {
        width: 100%;
        height: 169px;
        display: block;
        margin: 0;
        color: #000;
        overflow: hidden;
        border-radius: 8px;
        border: 1px solid var(--app-border);
        background: linear-gradient(180deg, #fff, #fafafa);
        box-shadow: 0 10px 24px rgba(24, 24, 27, .06);
        transition: transform .2s ease, box-shadow .2s ease, border-color .2s ease;

        &:hover {
          transform: translateY(-4px);
          border-color: var(--app-accent);
          box-shadow: 0 16px 34px rgba(24, 24, 27, .14);
        }

        a {
          display: block;
          height: 100%;
        }

        img {
          width: 118px;
          height: 167px;
          overflow: hidden;
          position: relative;
          display: inline-block;
          border: 1px solid var(--app-border);
          border-radius: 8px;
          object-fit: cover;
        }

        .info {
          width: calc(100% - 126px);
          height: 100%;
          position: relative;
          padding: 10px 12px 10px 14px;
          display: inline-block;
          vertical-align: top;

          .img-title {
            line-height: 20px;
            font-size: 14px;
            color: var(--app-text);
            overflow: hidden;
            display: -webkit-box;
            -webkit-box-orient: vertical;
            -webkit-line-clamp: 2;
          }

          .local {
            width: 100%;
            display: -webkit-box;
            -webkit-box-orient: vertical;
            -webkit-line-clamp: 2;
            font-size: 12px;
            margin-top: 12px;
            color: #9B9B9B;
            overflow: hidden;
            word-break: break-all;
          }

          .showTime {
            width: 100%;
            display: -webkit-box;
            -webkit-box-orient: vertical;
            -webkit-line-clamp: 2;
            font-size: 12px;
            margin-top: 10px;
            color: #9B9B9B;
            overflow: hidden;
            word-break: break-all;
          }

          .price {
            width: calc(100% - 24px);
            position: absolute;
            left: 14px;
            bottom: 8px;
            font-size: 19px;
            color: var(--app-danger);
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
            font-weight: bold;

            .rise {
              font-size: 14px;
            }
          }
        }
      }

    }
  }
}


</style>
