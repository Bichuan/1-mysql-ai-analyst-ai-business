import http from './http'

/**
 * 只向后端提交自然语言问题。
 * SQL 的生成、审核与执行始终留在服务端，前端不能也不应构造原始 SQL。
 */
export function executeNaturalLanguageQuery(question, conversationId) {
  const payload = { question }
  if (conversationId) payload.conversationId = conversationId
  return http.post('/queries/query', payload)
}
