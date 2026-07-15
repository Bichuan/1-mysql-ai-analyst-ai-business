import axios from 'axios'
import { ElMessage } from 'element-plus'
import { authState, clearAuth } from '../utils/auth'

const http = axios.create({
  baseURL: '/api',
  timeout: 90_000
})

let redirectingToLogin = false

http.interceptors.request.use((config) => {
  // JWT 始终通过 Authorization 请求头携带；接口真实鉴权仍完全由后端 Spring Security 执行。
  if (authState.accessToken) {
    config.headers.Authorization = `Bearer ${authState.accessToken}`
  }
  return config
})

function redirectToLogin() {
  clearAuth()
  if (window.location.pathname.startsWith('/login')) return false
  if (redirectingToLogin) return true

  redirectingToLogin = true
  // 不反向导入 router，避免“路由 -> 页面 -> API -> 路由”的循环依赖。
  // 同时保留原页面，用户重新登录后可以继续刚才的操作。
  const redirect = `${window.location.pathname}${window.location.search}${window.location.hash}`
  const params = new URLSearchParams({ reason: 'expired', redirect })
  window.location.replace(`/login?${params.toString()}`)
  return true
}

http.interceptors.response.use(
  (response) => {
    const envelope = response.data
    if (!envelope || typeof envelope.code !== 'number') {
      return envelope
    }
    if (envelope.code === 0) {
      return envelope.data
    }

    const message = envelope.message || '请求失败'
    if (envelope.code === 40100 && redirectToLogin()) {
      const error = new Error('登录状态已过期，请重新登录')
      error.code = envelope.code
      return Promise.reject(error)
    }
    ElMessage.error(message)
    const error = new Error(message)
    error.code = envelope.code
    return Promise.reject(error)
  },
  (error) => {
    const message = error.response?.data?.message || error.message || '网络请求失败，请稍后重试'
    if (error.response?.status === 401 && redirectToLogin()) {
      return Promise.reject(new Error('登录状态已过期，请重新登录'))
    }
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

export default http
