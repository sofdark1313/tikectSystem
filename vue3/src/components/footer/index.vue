<template>
  <div class="footer">
    <div class="footer-sec">
      <ul class="footer-links">
<!--        <li v-for="item in footerList">{{ item }}<span class="line">|</span></li>-->
        <li><a href="/help.html" target="_blank">帮助中心</a><span class="line">|</span></li>
        <li><a href="/ticket-notice.html" target="_blank">购票须知</a><span class="line">|</span></li>
        <li><a href="/customer-service.html" target="_blank">客服支持</a><span class="line">|</span></li>
      </ul>
<!--      <div class="footer-ft">-->
<!--        <router-link to="/index" class="link">-->
<!--          <img :src="logo2" alt="">-->
<!--        </router-link>-->
<!--        <div class="code">-->
<!--          <img :src="code" alt="">-->
<!--          <span>APP下载</span>-->
<!--        </div>-->
<!--      </div>-->
      <div class="footer-rt">
<!--        <el-button-->
<!--            type="primary"-->
<!--            style="width:100%;"-->
<!--            class="elBtn"-->
<!--        ><span>在线客服</span>-->
<!--        </el-button>-->
        <div class="footer-details-info">
          <ul class="footer-links">
            <li ><a href="https://beian.miit.gov.cn/" target="_blank">京ICP备2024068560号-2</a><span class="line">|</span></li>
            <li ><a href="https://beian.mps.gov.cn/#/query/webSearch" target="_blank">京公网安备11011402054237号</a></li>
          </ul>

        </div>
<!--        <ul class="footer-details">-->
<!--          <li><a href="https://beian.miit.gov.cn/" target="_blank">京ICP备2024068560号-1</a></li>-->
<!--          <li>举报投诉邮箱：tikectsystem_tousu@member.alibaba.com</li>-->
<!--        </ul>-->
      </div>
    </div>
  </div>
</template>

<script setup>
</script>

<style scoped lang="scss">
.footer {
  background: #111113;
  width: 100%;
  height: 158px;
  border-top: 3px solid var(--app-accent);
  box-shadow: 0 -14px 38px rgba(24, 24, 27, .10);
  //position: fixed;

  .footer-sec {
    width: 1200px;
    margin: 20px auto 0 auto;

    .footer-links {
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 36px 0;
      text-align: center;
      zoom: 1;
      list-style: none;
      font-size: 13px;
      color: rgba(255, 255, 255, .68);

      li {
        transition: color .2s ease;
        a {
          color: inherit;
          text-decoration: none;
        }

        &:hover {
          color: var(--app-accent);
        }
        &:last-child {
          .line {
            display: none !important;
          }
        }

        .line {
          padding: 0 24px;
          color: rgba(255, 255, 255, .22);


        }
      }


    }

    .footer-ft {
      zoom: 1;
      float: left;
      margin-top: 20px;
      padding-bottom: 58px;
      margin-right: 100px;

      .link {
        img {

        }
      }

      .code {

        text-align: center;
        margin-left: 40px;
        float: right;

        img {
          display: inline-block;
          width: 87px;
          height: 87px;
          margin-bottom: 8px;
        }

        span {
          font-size: 14px;
          color: #111;
          text-align: center;
          width: 87px;
          display: block;
        }

      }
    }

    .footer-rt {
      zoom: 1;
      padding-bottom: 10px;

      .elBtn {
        float: left;
        display: block;
        width: 98px !important;
        height: 30px;
        line-height: 1;
        text-align: center;
        background-color: var(--app-primary);
        color: #fff;
        font-size: 14px;
        border-radius: 20px;
        margin-top: 20px;
        border: none;
        margin-bottom: 10px;

        span {
          display: inline-flex;
          align-items: center;
        }
      }

      .footer-details {
        list-style: none;

        li {
          width: 69%;
          height: 20px;
          float: left;
          font-size: 13px;
          color: #111;
          margin-top: 10px;
        }
      }
      .footer-details-info{
        width: 1200px;
        height: 20px;
        text-align: center;
        font-size: 14px;
        color: rgba(255, 255, 255, .58);

        a {
          color: rgba(255, 255, 255, .58);

          &:hover {
            color: var(--app-accent);
          }
        }
      }
    }
  }

}

.btn {
  background-color: var(--app-primary);
  background-image: linear-gradient(90deg, var(--app-primary), var(--app-accent));
  border-color: var(--app-primary);
  border-radius: 3px;
  font-size: 20px;
  height: 42px;
  line-height: 42px;
  outline: none;
  color: #fff;
  width: 100%;
  cursor: pointer;
}
</style>
