import axios from 'axios'
import { KJUR, hextob64 } from 'jsrsasign'
import {
    getToken,
    getRefreshToken,
    setToken,
    setRefreshToken,
    removeToken,
    removeRefreshToken
} from '@/utils/auth'
import useUserStore from '@/store/modules/user'
import { removeLegacyBrandFromData } from '@/utils/index'

axios.defaults.headers['Content-Type'] = 'application/json;charset=utf-8'

export let isRelogin = { show: false }

const defaultHeaders = import.meta.env.VITE_SIGN_FLAG == 1 ? { no_verify: false } : { no_verify: true }

const request = axios.create({
    baseURL: import.meta.env.VITE_APP_BASE_API,
    timeout: 10000,
    headers: defaultHeaders
})

const refreshRequest = axios.create({
    baseURL: import.meta.env.VITE_APP_BASE_API,
    timeout: 10000,
    headers: defaultHeaders
})

let refreshTokenPromise = null

request.interceptors.request.use(
    config => {
        const signFlag = import.meta.env.VITE_SIGN_FLAG
        if (config.data !== undefined && config.data !== null && config.data !== '' && signFlag == 1 && !config._signed) {
            config.data = sign(config.data)
            config._signed = true
        }
        const token = getToken()
        if (token) {
            config.headers = Object.assign(config.headers || {}, { token })
        }
        return config
    },
    error => Promise.reject(error)
)

request.interceptors.response.use(
    response => {
        if (response.config.url === '/tikectsystem/user/user/logout') {
            return response.data
        }
        const data = response.data
        const code = String(data && data.code)
        if (code === '10055') {
            return refreshAccessTokenAndRetry(response.config)
        }
        if (code === '516') {
            return promptRelogin()
        }
        return removeLegacyBrandFromData(data)
    },
    error => Promise.reject(error)
)

function refreshAccessTokenAndRetry(originalConfig) {
    if (originalConfig._retry) {
        return promptRelogin()
    }
    const refreshToken = getRefreshToken()
    if (!refreshToken) {
        return promptRelogin()
    }
    originalConfig._retry = true
    return refreshTokens(refreshToken).then(accessToken => {
        originalConfig.headers = Object.assign(originalConfig.headers || {}, { token: accessToken })
        return request(originalConfig)
    }).catch(() => {
        removeToken()
        removeRefreshToken()
        return promptRelogin()
    })
}

function refreshTokens(refreshToken) {
    if (!refreshTokenPromise) {
        const data = buildRefreshTokenData(refreshToken)
        refreshTokenPromise = refreshRequest.post('/tikectsystem/user/user/token/refresh', data)
            .then(response => {
                if (!response.data || response.data.code != 0 || !response.data.data) {
                    throw new Error('refresh token failed')
                }
                const responseData = response.data.data
                const accessToken = responseData.accessToken || responseData.token
                if (!accessToken || !responseData.refreshToken) {
                    throw new Error('refresh token response invalid')
                }
                setToken(accessToken, responseData.expiresIn)
                setRefreshToken(responseData.refreshToken, responseData.refreshExpiresIn)
                const userStore = useUserStore()
                userStore.token = accessToken
                userStore.refreshToken = responseData.refreshToken
                return accessToken
            })
            .finally(() => {
                refreshTokenPromise = null
            })
    }
    return refreshTokenPromise
}

function buildRefreshTokenData(refreshToken) {
    const data = {
        code: import.meta.env.VITE_CODE,
        refreshToken
    }
    return import.meta.env.VITE_SIGN_FLAG == 1 ? sign(data) : data
}

function promptRelogin() {
    if (!isRelogin.show) {
        isRelogin.show = true
        ElMessageBox.confirm('登录状态已过期，请重新登录', '系统提示', {
            confirmButtonText: '重新登录',
            cancelButtonText: '取消',
            type: 'warning'
        }).then(() => {
            isRelogin.show = false
            useUserStore().logOut().finally(() => {
                redirectToLogin()
            })
        }).catch(() => {
            isRelogin.show = false
        })
    }
    return Promise.reject('登录状态已过期，请重新登录')
}

function redirectToLogin() {
    const currentPath = `${location.pathname}${location.search}${location.hash}`
    if (currentPath && currentPath !== '/login' && !currentPath.startsWith('/login?')) {
        location.href = `/login?redirect=${encodeURIComponent(currentPath)}`
        return
    }
    location.href = '/login'
}

export function sign(params) {
    const code = import.meta.env.VITE_CODE
    const paramsStr = JSON.stringify(params)
    const signParam = { businessBody: paramsStr, code: code }
    const signSecretKey = import.meta.env.VITE_SIGN_SECRET_KEY
    if (!signSecretKey) {
        throw new Error('签名私钥未配置，无法发起签名请求')
    }
    const privateKey = signSecretKey.includes('BEGIN PRIVATE KEY')
        ? signSecretKey
        : `-----BEGIN PRIVATE KEY-----\n${signSecretKey}\n-----END PRIVATE KEY-----`

    const sig = new KJUR.crypto.Signature({ alg: 'SHA256withRSA' })
    sig.init(privateKey)
    sig.updateString(buildParam(signParam))
    const sign = hextob64(sig.sign())

    return { code: code, businessBody: paramsStr, sign: sign }
}

const buildKeyValue = (key, value, isEncode) => {
    let result = `${key}=`
    if (isEncode) {
        try {
            result += encodeURIComponent(value)
        } catch (error) {
            result += value
        }
    } else {
        result += value
    }
    return result
}

const buildParam = (params) => {
    const keys = Object.keys(params).sort()
    let queryString = ''

    keys.forEach((key, index) => {
        const value = params[key]
        queryString += buildKeyValue(key, value, false)
        if (index < keys.length - 1) {
            queryString += '&'
        }
    })

    return queryString
}

export default request
