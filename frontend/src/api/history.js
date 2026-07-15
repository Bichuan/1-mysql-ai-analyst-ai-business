import http from './http'

/**
 * 用户身份只从 JWT 中获取，前端不传 userId，避免越权查询他人的审计历史。
 */
export function getQueryHistories(page = 1, size = 10) {
  return http.get('/query-histories', { params: { page, size } })
}
