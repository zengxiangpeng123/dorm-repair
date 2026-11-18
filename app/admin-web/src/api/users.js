import { api } from './request'
export const userApi = {
  list: () => api.get('/admin/users'),
  create: (body) => api.post('/admin/users', body),
  resetPassword: (id, password) => api.post(`/admin/users/${id}/password`, { password })
}
