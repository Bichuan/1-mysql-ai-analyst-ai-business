<script setup>
import { onMounted, ref } from 'vue'
import { getHealth } from '../api/health'
import { authState } from '../utils/auth'

const health = ref(null)
const loading = ref(false)

async function loadHealth() {
  loading.value = true
  try {
    health.value = await getHealth()
  } finally {
    loading.value = false
  }
}

onMounted(loadHealth)
</script>

<template>
  <section class="dashboard">
    <div class="welcome-banner">
      <div>
        <p class="eyebrow">READY TO ANALYZE</p>
        <h2>你好，{{ authState.user?.nickname || authState.user?.username }}。</h2>
        <p>后端安全能力已就绪。下一步将在智能查询中心完成自然语言问题到分析结论的完整展示。</p>
      </div>
      <el-button type="primary" @click="$router.push('/query')">进入智能查询</el-button>
    </div>

    <div class="metric-grid">
      <article class="metric-card"><span>数据源隔离</span><strong>双数据源</strong><p>系统库读写，业务库最小只读权限。</p></article>
      <article class="metric-card"><span>SQL 安全</span><strong>五层防线</strong><p>AST 审核、LIMIT、超时与数据库权限兜底。</p></article>
      <article class="metric-card"><span>性能保护</span><strong>Redis</strong><p>限流、语义缓存与异步审计历史。</p></article>
    </div>

    <el-card shadow="never" class="health-card">
      <template #header>
        <div class="card-header"><span>后端连通状态</span><el-button text :loading="loading" @click="loadHealth">刷新</el-button></div>
      </template>
      <div class="health-content">
        <el-tag :type="health?.status === 'UP' ? 'success' : 'info'" effect="light">{{ health?.status || '检测中' }}</el-tag>
        <span>{{ health?.application || '正在连接 Spring Boot 服务' }}</span>
      </div>
    </el-card>
  </section>
</template>
