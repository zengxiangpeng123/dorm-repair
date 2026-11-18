import { api } from './request'
export const authApi = {
  login: (account, password) => api.post('/auth/login', { account, password }),
  logout: () => api.post('/auth/logout', {}),
  me: () => api.get('/auth/me')
}
