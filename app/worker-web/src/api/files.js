import { api } from './request'

export const fileApi = {
  upload: (file, purpose) => {
    const form = new FormData()
    form.append('file', file)
    form.append('purpose', purpose)
    return api.post('/files', form)
  },
  blob: (id) => api.blob(`/files/${id}`)
}
