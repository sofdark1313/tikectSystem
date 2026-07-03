import router from './router/router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { getToken } from '@/utils/auth'

NProgress.configure({ showSpinner: false });

const whiteList = ['/login', '/register'];
router.beforeEach((to, from, next) => {
    NProgress.start()
    const hasToken = getToken()
    if (hasToken) {
        // to.meta.title&& useSettingsStore().setTitle(to.meta.title)
        if (to.path === '/login') {
            next({ path: '/' })
        } else {
            next()
            // if (useUserStore().roles.length === 0) {
            // isRelogin.show = true
            // 判断当前用户是否已拉取完user_info信息
            // useUserStore().getInfo().then(() => {
            //     isRelogin.show = false
            //     usePermissionStore().generateRoutes().then(accessRoutes => {
            //         // 根据roles权限生成可访问的路由表
            //         accessRoutes.forEach(route => {
            //             if (!isHttp(route.path)) {
            //                 router.addRoute(route) // 动态添加可访问路由表
            //             }
            //         })
            //         next({ ...to, replace: true }) // hack方法 确保addRoutes已完成
            //     })
            // }).catch(err => {
            //     useUserStore().logOut().then(() => {
            //         ElMessage.error(err)
            //         next({ path: '/' })
            //     })
            // })
            // } else {
            //     next()
            // }
        }
    }
    else {
        if (whiteList.indexOf(to.path) !== -1 || !to.matched.some(record => record.meta.requiresAuth)) {
            next()
        } else {
            next({ path: '/login', query: { redirect: to.fullPath } })
        }
    }
})

router.afterEach(() => {
    NProgress.done()
})
