import { api } from './request'
export const studentApi = {
  meta: () => api.get('/meta'),
  myOrders: (status) => api.get('/my/orders', status ? { status } : {}),
  detail: (no) => api.get(`/orders/${no}`),
  submit: (body) => api.post('/orders', body),
  accept: (no, body) => api.post(`/orders/${no}/accept`, body),
  reworkRequest: (no, body) => api.post(`/orders/${no}/rework-request`, body),
  cancel: (no, body) => api.post(`/orders/${no}/cancel`, body),
  cancelRequest: (no, body) => api.post(`/orders/${no}/cancel-request`, body)
}
