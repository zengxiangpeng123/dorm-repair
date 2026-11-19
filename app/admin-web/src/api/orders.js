import { api } from './request'
export const adminApi = {
  meta: () => api.get('/meta'),
  matrix: () => api.get('/state/matrix'),
  transitions: () => api.get('/state/transitions'),
  aiHealth: () => api.get('/ai/health'),
  list: (params) => api.get('/orders', params),
  detail: (no) => api.get(`/orders/${no}`),
  dashboard: () => api.get('/admin/dashboard'),
  exceptions: (kind = 'ALL') => api.get('/admin/exceptions', { kind }),
  config: () => api.get('/admin/config'),
  saveConfig: (body) => api.put('/admin/config', body),
  reworkReviews: () => api.get('/admin/rework-reviews'),
  aiReviews: (page = 1, pageSize = 50) => api.get('/admin/ai-reviews', { page, pageSize }),
  reworkReview: (no, body) => api.post(`/orders/${no}/rework-review`, body),
  adminCancel: (no, body) => api.post(`/orders/${no}/admin-cancel`, body),
  assignBuilding: (id, body = {}) => api.post(`/buildings/${id}/assign`, body),
  partsArrived: (no, body) => api.post(`/orders/${no}/parts-arrived`, body),
}
