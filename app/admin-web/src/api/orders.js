import { api } from './request'
export const adminApi = {
  meta: () => api.get('/meta'),
  matrix: () => api.get('/state/matrix'),
  transitions: () => api.get('/state/transitions'),
  aiHealth: () => api.get('/ai/health'),
  list: (params) => api.get('/orders', params),
  detail: (no) => api.get(`/orders/${no}`),
  dashboard: () => api.get('/admin/dashboard'),
  exceptions: () => api.get('/admin/exceptions'),
  config: () => api.get('/admin/config'),
  saveConfig: (body) => api.put('/admin/config', body),
  reworkReviews: () => api.get('/admin/rework-reviews'),
  aiReviews: (limit = 50) => api.get('/admin/ai-reviews', { limit }),
  reworkReview: (no, body) => api.post(`/orders/${no}/rework-review`, body),
  adminCancel: (no, body) => api.post(`/orders/${no}/admin-cancel`, body),
  partsArrived: (no, body) => api.post(`/orders/${no}/parts-arrived`, body),
}
