import { api } from './request'

/**
 * 命令与查询的薄封装。路径与 SYS-003 一一对应，改路径只改这里。
 *
 * 第三个参数 opts 用来临时换身份：{ role: 'system' } —— 派工、配件到货、定时器这类
 * 动作的合法触发方是 SYSTEM，页面替系统跑时必须显式说明，不然服务端按当前端的身份判 403。
 */
export const ordersApi = {
  meta: () => api.get('/meta'),
  matrix: () => api.get('/state/matrix'),
  transitions: () => api.get('/state/transitions'),
  aiHealth: () => api.get('/ai/health'),

  myOrders: (status) => api.get('/my/orders', status ? { status } : {}),
  workerTodo: () => api.get('/worker/todo'),
  board: (id, floors) => api.get(`/buildings/${id}/board`, floors ? { floors } : {}),

  exceptions: (kind) => api.get('/admin/exceptions', kind ? { kind } : {}),
  dashboard: () => api.get('/admin/dashboard'),
  config: () => api.get('/admin/config'),
  saveConfig: (body) => api.put('/admin/config', body),

  list: (params) => api.get('/orders', params),
  detail: (no) => api.get(`/orders/${no}`),
  events: (no, params = {}) => api.get(`/orders/${no}/events`, params),

  submit: (body, opts) => api.post('/orders', body, opts),
  assign: (no, body, opts) => api.post(`/orders/${no}/assign`, body, opts),
  arrive: (no, body, opts) => api.post(`/orders/${no}/arrive`, body, opts),
  noShow: (no, body, opts) => api.post(`/orders/${no}/no-show`, body, opts),
  finish: (no, body, opts) => api.post(`/orders/${no}/finish`, body, opts),
  needParts: (no, body, opts) => api.post(`/orders/${no}/need-parts`, body, opts),
  partsArrived: (no, body, opts) => api.post(`/orders/${no}/parts-arrived`, body, opts),
  accept: (no, body, opts) => api.post(`/orders/${no}/accept`, body, opts),
  reject: (no, body, opts) => api.post(`/orders/${no}/reject`, body, opts),
  reworkRequest: (no, body, opts) => api.post(`/orders/${no}/rework-request`, body, opts),
  reworkReview: (no, body, opts) => api.post(`/orders/${no}/rework-review`, body, opts),
  reworkReviews: () => api.get('/admin/rework-reviews'),
  cancel: (no, body, opts) => api.post(`/orders/${no}/cancel`, body, opts),
  cancelRequest: (no, body, opts) => api.post(`/orders/${no}/cancel-request`, body, opts),
  adminCancel: (no, body, opts) => api.post(`/orders/${no}/admin-cancel`, body, opts),
  arriveBuilding: (id, body, opts) => api.post(`/buildings/${id}/arrive`, body, opts),

  reset: () => api.post('/demo/reset', {}, { role: 'system' }),
  advance: (hours = 168) => api.post(`/demo/advance?hours=${hours}`, {}, { role: 'system' })
}
