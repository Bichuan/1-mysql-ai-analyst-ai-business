<script setup>
import { useRoute, useRouter } from 'vue-router'
import { authState, clearAuth } from '../utils/auth'

const router = useRouter()
const route = useRoute()

function handleMenuSelect(path) {
  router.push(path)
}

function logout() {
  clearAuth()
  router.replace({ name: 'login' })
}
</script>

<template>
  <el-container class="app-shell">
    <el-aside width="244px" class="app-sidebar">
      <div class="sidebar-brand">
        <div class="brand-mark brand-mark--small">AI</div>
        <div>
          <strong>数据分析助手</strong>
          <span>Enterprise Console</span>
        </div>
      </div>

      <el-menu :default-active="route.path" class="sidebar-menu" @select="handleMenuSelect">
        <el-menu-item index="/dashboard"><span>工作台</span></el-menu-item>
        <el-menu-item index="/query"><span>智能查询</span></el-menu-item>
        <el-menu-item index="/history"><span>查询历史</span></el-menu-item>
      </el-menu>

      <div class="sidebar-footnote">企业级 AI 数据查询<br />Spring Boot · MySQL · Redis</div>
    </el-aside>

    <el-container>
      <el-header class="app-header">
        <div>
          <p class="eyebrow">ANALYTICS WORKSPACE</p>
          <h1>{{ route.meta.title || '企业数据分析工作台' }}</h1>
        </div>
        <!-- 点击触发比默认悬停更适合触屏设备，也能让退出入口具有稳定、可测试的交互。 -->
        <el-dropdown trigger="click" @command="logout">
          <button class="user-entry" type="button">
            <el-avatar :size="34">{{ authState.user?.nickname?.slice(0, 1) || 'U' }}</el-avatar>
            <span>
              <strong>{{ authState.user?.nickname || authState.user?.username }}</strong>
              <small>{{ authState.user?.role || 'USER' }}</small>
            </span>
          </button>
          <template #dropdown>
            <el-dropdown-menu><el-dropdown-item command="logout">退出登录</el-dropdown-item></el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>

      <el-main class="app-main"><router-view /></el-main>
    </el-container>
  </el-container>
</template>
