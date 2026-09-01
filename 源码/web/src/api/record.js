import request from '../utils/request'

export const pageRecord = (query) => request.post('/record/page', query)
export const addRecord = (data) => request.post('/record', data)
export const updateRecord = (id, data) => request.put(`/record/${id}`, data)
export const deleteRecord = (id) => request.delete(`/record/${id}`)
export const recordOptions = () => request.get('/record/options')
