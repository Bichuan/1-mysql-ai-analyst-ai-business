<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getQueryHistories } from '../api/history'

const router = useRouter()
const loading = ref(false)
const loadError = ref('')
const detailVisible = ref(false)
const selectedHistory = ref(null)
const pageData = reactive({
  records: [],
  total: 0,
  page: 1,
  size: 10,
  pages: 0
})

async function loadHistories() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await getQueryHistories(pageData.page, pageData.size)
    pageData.records = data?.records || []
    pageData.total = Number(data?.total || 0)
    pageData.page = Number(data?.page || 1)
    pageData.size = Number(data?.size || pageData.size)
    pageData.pages = Number(data?.pages || 0)
  } catch (error) {
    loadError.value = error.message || '查询历史加载失败'
  } finally {
    loading.value = false
  }
}

function changePage(page) {
  pageData.page = page
  loadHistories()
}

function changePageSize(size) {
  pageData.page = 1
  pageData.size = size
  loadHistories()
}

function showDetail(row) {
  selectedHistory.value = row
  detailVisible.value = true
}

function askAgain(row) {
  router.push({ name: 'query', query: { question: row.question } })
}

function formatDateTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

function statusType(status) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  return 'warning'
}

function statusLabel(status) {
  return { SUCCESS: '成功', FAILED: '失败', PROCESSING: '处理中' }[status] || status || '未知'
}

function auditType(result) {
  if (result === 'PASS') return 'success'
  if (result === 'REJECT') return 'danger'
  return 'info'
}

onMounted(loadHistories)
</script>

<template>
  <section class="history-page">
    <div class="history-page__intro">
      <div>
        <p class="eyebrow">AUDIT TRAIL</p>
        <h2>我的查询历史</h2>
        <p>查看自然语言问题、审核后的 SQL、执行状态和 AI 总结。记录范围由后端根据当前 JWT 用户隔离。</p>
      </div>
      <el-button :loading="loading" @click="loadHistories">刷新记录</el-button>
    </div>

    <div class="history-overview">
      <article><span>历史记录总数</span><strong>{{ pageData.total }}</strong></article>
      <article><span>当前页</span><strong>{{ pageData.page }} / {{ pageData.pages || 1 }}</strong></article>
      <article><span>数据安全</span><strong>仅当前用户</strong></article>
    </div>

    <el-alert
      v-if="loadError"
      class="history-error"
      type="error"
      :closable="false"
      show-icon
      :title="loadError"
      description="请检查后端连接后重新加载。"
    />

    <el-card shadow="never" class="history-card">
      <el-table v-loading="loading" :data="pageData.records" stripe class="history-table">
        <el-table-column label="查询时间" width="172">
          <template #default="scope">{{ formatDateTime(scope.row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="question" label="自然语言问题" min-width="290" show-overflow-tooltip />
        <el-table-column label="状态" width="92" align="center">
          <template #default="scope">
            <el-tag :type="statusType(scope.row.status)" effect="light">{{ statusLabel(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="SQL 审核" width="105" align="center">
          <template #default="scope">
            <el-tag :type="auditType(scope.row.sqlAuditResult)" effect="plain">{{ scope.row.sqlAuditResult || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="执行耗时" width="110" align="right">
          <template #default="scope">{{ scope.row.executionTime == null ? '-' : `${scope.row.executionTime} ms` }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right" align="center">
          <template #default="scope">
            <el-button text type="primary" @click="showDetail(scope.row)">查看详情</el-button>
            <el-button text :disabled="!scope.row.question" @click="askAgain(scope.row)">再次提问</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="还没有查询历史，先去智能查询中心提一个问题吧" :image-size="82" />
        </template>
      </el-table>

      <div v-if="pageData.total > 0" class="history-pagination">
        <span>共 {{ pageData.total }} 条审计记录</span>
        <el-pagination
          background
          layout="sizes, prev, pager, next"
          :current-page="pageData.page"
          :page-size="pageData.size"
          :page-sizes="[10, 20, 50]"
          :total="pageData.total"
          @current-change="changePage"
          @size-change="changePageSize"
        />
      </div>
    </el-card>

    <el-dialog v-model="detailVisible" title="查询审计详情" width="760px" class="history-detail-dialog">
      <template v-if="selectedHistory">
        <div class="history-detail-meta">
          <el-tag :type="statusType(selectedHistory.status)">{{ statusLabel(selectedHistory.status) }}</el-tag>
          <span>{{ formatDateTime(selectedHistory.createdAt) }}</span>
          <span v-if="selectedHistory.executionTime != null">耗时 {{ selectedHistory.executionTime }} ms</span>
        </div>

        <div class="history-detail-block">
          <label>自然语言问题</label>
          <p>{{ selectedHistory.question || '-' }}</p>
        </div>
        <div class="history-detail-block">
          <label>审核后的 SQL</label>
          <pre><code>{{ selectedHistory.sql || '本条记录没有生成 SQL' }}</code></pre>
        </div>
        <div class="history-detail-grid">
          <div class="history-detail-block">
            <label>SQL 审核结果</label>
            <p>{{ selectedHistory.sqlAuditResult || '-' }}</p>
          </div>
          <div class="history-detail-block">
            <label>审核说明</label>
            <p>{{ selectedHistory.sqlAuditReason || '-' }}</p>
          </div>
        </div>
        <div class="history-detail-block">
          <label>AI 业务总结</label>
          <p class="history-detail-summary">{{ selectedHistory.summary || '本条记录没有生成 AI 总结。' }}</p>
        </div>
        <el-alert
          v-if="selectedHistory.errorMessage"
          type="error"
          :closable="false"
          show-icon
          title="执行失败信息"
          :description="selectedHistory.errorMessage"
        />
      </template>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button type="primary" @click="askAgain(selectedHistory)">使用该问题再次查询</el-button>
      </template>
    </el-dialog>
  </section>
</template>
