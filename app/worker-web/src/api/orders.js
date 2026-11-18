import { api } from './request'
export const workerApi = {
  meta: () => api.get('/meta'),
  workerTodo: () => api.get('/worker/todo'),
  board: (id, floors) => api.get(`/buildings/${id}/board`, floors ? { floors } : {}),
  detail: (no) => api.get(`/orders/${no}`),
  arrive: (no, body) => api.post(`/orders/${no}/arrive`, body),
  arriveBuilding: (id, body) => api.post(`/buildings/${id}/arrive`, body),
  noShow: (no, body) => api.post(`/orders/${no}/no-show`, body),
  finish: (no, body) => api.post(`/orders/${no}/finish`, body),
  needParts: (no, body) => api.post(`/orders/${no}/need-parts`, body),
}
