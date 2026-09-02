import request from '../utils/request'

export const importConfig = () => request.get('/import/config')

export const createImportJob = (files, memberId) => {
  const form = new FormData()
  files.forEach((f) => form.append('files', f))
  if (memberId) {
    form.append('memberId', memberId)
  }
  return request.post('/import/jobs', form, { timeout: 120000 })
}

export const getImportJob = (id) => request.get(`/import/jobs/${id}`)

export const confirmImportJob = (id, itemIds, merge = false) =>
  request.post(`/import/jobs/${id}/confirm`, { itemIds, merge }, { timeout: 120000 })
