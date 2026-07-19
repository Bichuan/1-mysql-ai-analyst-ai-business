<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import { executeNaturalLanguageQuery } from '../api/query'

const route = useRoute()
const question = ref('')
const loading = ref(false)
const result = ref(null)
const conversationId = ref(null)
const requestError = ref('')
const resultSection = ref(null)

const examples = [
  '查询今年销售额最高的10个客户',
  '查询华东地区的VIP客户数量',
  '查询今年已完成订单的总销售额',
  '按地区统计今年的销售额'
]

// 返回列由后端动态 SQL 决定，不能在前端写死实体字段；这里合并每一行的键以兼容聚合查询结果。
const resultColumns = computed(() => {
  const columns = new Set()
  result.value?.rows?.forEach((row) => {
    Object.keys(row || {}).forEach((key) => columns.add(key))
  })
  return [...columns]
})

function chooseExample(example) {
  question.value = example
  requestError.value = ''
}

async function runQuery() {
  const normalizedQuestion = question.value.trim()
  if (!normalizedQuestion) {
    ElMessage.warning('请输入想查询的业务问题')
    return
  }

  loading.value = true
  requestError.value = ''
  try {
    result.value = await executeNaturalLanguageQuery(normalizedQuestion, conversationId.value)
    conversationId.value = result.value?.conversationId || null
    await nextTick()
    resultSection.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  } catch (error) {
    // HTTP 拦截器会统一提示后端的业务错误；页面保留错误态，方便用户直接修改问题后重试。
    requestError.value = error.message || '查询未完成，请稍后重试'
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  question.value = ''
  result.value = null
  conversationId.value = null
  requestError.value = ''
}

async function copySql() {
  if (!result.value?.sql) return
  try {
    await navigator.clipboard.writeText(result.value.sql)
    ElMessage.success('SQL 已复制')
  } catch {
    ElMessage.warning('当前浏览器不允许复制，请手动复制 SQL')
  }
}

function formatCell(value) {
  if (value === null || value === undefined || value === '') return '-'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

onMounted(() => {
  // 查询历史的“再次提问”只回填问题，不自动执行，避免页面跳转意外消耗限流令牌和模型额度。
  if (typeof route.query.question === 'string') {
    question.value = route.query.question.slice(0, 500)
  }
})
</script>

<template>
  <section class="query-page">
    <div class="query-page__intro">
      <div>
        <p class="eyebrow">NATURAL LANGUAGE TO INSIGHT</p>
        <h2>问一句，获得可审计的数据结论</h2>
        <p>仅输入业务问题。SQL 由后端生成并经过安全审核，数据使用只读账号查询且会在分析前脱敏。</p>
      </div>
      <div class="query-safety-badges" aria-label="查询安全能力">
        <span>只读执行</span>
        <span>SQL 审核</span>
        <span>结果脱敏</span>
      </div>
    </div>

    <el-card shadow="never" class="query-composer-card">
      <div class="query-composer-card__header">
        <div>
          <h3>你想了解什么？</h3>
          <p>例如：查询今年销售额最高的 10 个客户</p>
        </div>
        <el-tag effect="plain" type="info">仅自然语言提问</el-tag>
      </div>

      <el-input
        v-model="question"
        type="textarea"
        :rows="4"
        :maxlength="500"
        show-word-limit
        resize="none"
        placeholder="输入与客户、订单、商品或销售额相关的业务问题…"
        @keydown.ctrl.enter.prevent="runQuery"
      />

      <div class="query-examples">
        <span>试试这样问：</span>
        <button v-for="example in examples" :key="example" type="button" @click="chooseExample(example)">{{ example }}</button>
      </div>

      <div class="query-composer-card__actions">
        <span>按 <kbd>Ctrl</kbd> + <kbd>Enter</kbd> 可快速提交</span>
        <div>
          <el-button :disabled="loading || !question" @click="resetQuery">清空</el-button>
          <el-button type="primary" :loading="loading" @click="runQuery">
            {{ loading ? 'AI 正在分析…' : '开始智能查询' }}
          </el-button>
        </div>
      </div>
    </el-card>

    <el-alert
      v-if="requestError"
      class="query-error"
      type="error"
      :closable="false"
      show-icon
      :title="requestError"
      description="请检查问题是否为允许的数据查询意图，或稍后重试。"
    />

    <section v-if="loading" class="query-loading" aria-live="polite">
      <div class="query-loading__orb"><span></span></div>
      <div>
        <strong>正在生成并审核 SQL</strong>
        <p>系统会依次执行安全校验、只读查询和 AI 结果总结，请稍候。</p>
      </div>
    </section>

    <section v-if="result && !loading" ref="resultSection" class="query-result">
      <div class="query-result__heading">
        <div>
          <p class="eyebrow">QUERY RESULT</p>
          <h2>查询已完成</h2>
          <p>{{ result.question }}</p>
        </div>
        <div class="result-status">
          <el-tag v-if="result.cacheHit" type="success" effect="light">Redis 缓存命中</el-tag>
          <el-tag type="info" effect="plain">返回 {{ result.rowCount }} 行</el-tag>
        </div>
      </div>

      <el-card shadow="never" class="summary-card">
        <template #header>
          <div class="result-card-header">
            <span>AI 业务总结</span>
            <el-tag type="primary" effect="light">脱敏数据分析</el-tag>
          </div>
        </template>
        <p class="summary-content">{{ result.summary || '本次查询未生成额外总结。' }}</p>
      </el-card>

      <el-card shadow="never" class="sql-card">
        <template #header>
          <div class="result-card-header">
            <span>审核后的只读 SQL</span>
            <el-button text type="primary" @click="copySql">复制 SQL</el-button>
          </div>
        </template>
        <pre><code>{{ result.sql }}</code></pre>
      </el-card>

      <el-card shadow="never" class="data-card">
        <template #header>
          <div class="result-card-header">
            <span>数据明细</span>
            <small>以下结果已按后端脱敏策略处理</small>
          </div>
        </template>
        <el-empty v-if="!result.rows?.length" description="本次查询没有匹配的数据" :image-size="76" />
        <el-table v-else :data="result.rows" stripe class="query-data-table" max-height="520">
          <el-table-column
            v-for="column in resultColumns"
            :key="column"
            :prop="column"
            :label="column"
            min-width="150"
            show-overflow-tooltip
          >
            <template #default="scope">{{ formatCell(scope.row[column]) }}</template>
          </el-table-column>
        </el-table>
      </el-card>
    </section>
  </section>
</template>
