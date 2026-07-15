<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { login, register } from '../api/auth'
import { saveLogin } from '../utils/auth'

const props = defineProps({
  initialMode: {
    type: String,
    default: 'login'
  }
})

const router = useRouter()
const route = useRoute()
const formRef = ref()
const submitting = ref(false)
const mode = ref(props.initialMode)
const form = reactive({
  username: '',
  password: '',
  nickname: '',
  email: ''
})

watch(() => props.initialMode, (value) => {
  mode.value = value
  formRef.value?.clearValidate()
})

const isLogin = computed(() => mode.value === 'login')
const rules = computed(() => ({
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: isLogin.value ? 1 : 4, max: 50, message: isLogin.value ? '用户名长度不能超过 50 个字符' : '用户名长度为 4 到 50 个字符', trigger: 'blur' },
    ...(isLogin.value ? [] : [{ pattern: /^[A-Za-z0-9_]+$/, message: '用户名仅支持字母、数字和下划线', trigger: 'blur' }])
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: isLogin.value ? 1 : 8, max: 64, message: isLogin.value ? '密码长度不能超过 64 个字符' : '密码长度为 8 到 64 个字符', trigger: 'blur' }
  ],
  nickname: [{ required: true, max: 50, message: '请输入不超过 50 个字符的昵称', trigger: 'blur' }],
  email: [{ type: 'email', max: 100, message: '请输入正确的邮箱地址', trigger: 'blur' }]
}))

function switchMode(nextMode) {
  router.replace({ name: nextMode === 'login' ? 'login' : 'register' })
}

function safeRedirect(value) {
  // 只允许站内相对地址，防止 redirect 参数被利用为外部跳转地址。
  return typeof value === 'string' && value.startsWith('/') && !value.startsWith('//')
    ? value
    : '/dashboard'
}

async function submit() {
  await formRef.value.validate()
  submitting.value = true
  try {
    if (isLogin.value) {
      const loginResult = await login({ username: form.username, password: form.password })
      saveLogin(loginResult)
      ElMessage.success(`欢迎回来，${loginResult.user.nickname || loginResult.user.username}`)
      await router.replace(safeRedirect(route.query.redirect))
      return
    }

    await register({
      username: form.username,
      password: form.password,
      nickname: form.nickname,
      email: form.email || undefined
    })
    ElMessage.success('注册成功，请使用新账号登录')
    form.password = ''
    await router.replace({ name: 'login' })
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  if (route.query.reason !== 'expired') return

  // 401 跳转完成后再提示，避免页面刷新导致消息一闪而过；随后移除 reason，刷新页面不会重复提示。
  ElMessage.warning({ message: '登录状态已过期，请重新登录', duration: 4500 })
  const redirect = safeRedirect(route.query.redirect)
  await router.replace({ name: 'login', query: { redirect } })
})
</script>

<template>
  <main class="auth-page">
    <section class="auth-brand">
      <!-- 纯 CSS 动效：不依赖图片资源，避免影响首屏加载速度。 -->
      <div class="brand-visual" aria-hidden="true">
        <span class="visual-orbit visual-orbit--outer"></span>
        <span class="visual-orbit visual-orbit--inner"></span>
        <span class="visual-core"><small>AI</small><b>01</b></span>
        <i class="visual-dot visual-dot--one"></i>
        <i class="visual-dot visual-dot--two"></i>
        <i class="visual-dot visual-dot--three"></i>
      </div>
      <div class="brand-mark">AI</div>
      <p class="eyebrow">ENTERPRISE INTELLIGENCE</p>
      <h1>企业数据分析助手</h1>
      <p class="brand-description">
        用自然语言查询企业数据，在安全审核、只读执行和结果脱敏的保护下获得可信分析结论。
      </p>
      <div class="brand-features">
        <span>Text-to-SQL</span>
        <span>五层 SQL 审核</span>
        <span>结果脱敏</span>
      </div>
      <div class="brand-status">
        <i></i>
        <span>安全数据链路已就绪</span>
        <small>LIVE</small>
      </div>
    </section>

    <section class="auth-panel">
      <div class="auth-card">
        <div class="auth-card__header">
          <p class="eyebrow">WELCOME</p>
          <h2>{{ isLogin ? '登录工作台' : '创建分析账号' }}</h2>
          <p>{{ isLogin ? '使用你的企业数据分析账号继续。' : '注册后即可体验安全的 AI 数据查询。' }}</p>
        </div>

        <div class="mode-switch">
          <button :class="{ active: isLogin }" type="button" @click="switchMode('login')">登录</button>
          <button :class="{ active: !isLogin }" type="button" @click="switchMode('register')">注册</button>
        </div>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
          <el-form-item label="用户名" prop="username">
            <el-input v-model.trim="form.username" autocomplete="username" placeholder="例如：analyst_01" />
          </el-form-item>
          <el-form-item v-if="!isLogin" label="昵称" prop="nickname">
            <el-input v-model.trim="form.nickname" placeholder="用于工作台展示" />
          </el-form-item>
          <el-form-item v-if="!isLogin" label="邮箱（可选）" prop="email">
            <el-input v-model.trim="form.email" autocomplete="email" placeholder="name@example.com" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" type="password" show-password autocomplete="current-password" placeholder="请输入密码" @keyup.enter="submit" />
          </el-form-item>
          <el-button class="submit-button" type="primary" native-type="submit" :loading="submitting">
            {{ isLogin ? '登录并进入工作台' : '创建账号' }}
          </el-button>
        </el-form>
      </div>
    </section>
  </main>
</template>
