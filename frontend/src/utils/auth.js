import { reactive } from 'vue'

const ACCESS_TOKEN_KEY = 'ai_data_analyst_access_token'
const USER_KEY = 'ai_data_analyst_user'

function readJson(key) {
  try {
    return JSON.parse(localStorage.getItem(key) || 'null')
  } catch {
    return null
  }
}

export const authState = reactive({
  accessToken: localStorage.getItem(ACCESS_TOKEN_KEY) || '',
  user: readJson(USER_KEY)
})

export function isAuthenticated() {
  return Boolean(authState.accessToken)
}

export function saveLogin(loginResult) {
  authState.accessToken = loginResult.accessToken
  authState.user = loginResult.user
  localStorage.setItem(ACCESS_TOKEN_KEY, loginResult.accessToken)
  localStorage.setItem(USER_KEY, JSON.stringify(loginResult.user))
}

export function clearAuth() {
  // 前端只保存短期 JWT 与可公开的用户信息；密码永远不进入 localStorage。
  authState.accessToken = ''
  authState.user = null
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}
