import request from '../utils/request'

export const pageRecord = (query) => request.post('/record/page', query)
export const addRecord = (data) => request.post('/record', data)
export const updateRecord = (id, data) => request.put(`/record/${id}`, data)
export const deleteRecord = (id) => request.delete(`/record/${id}`)
export const recordOptions = () => request.get('/record/options')
/** 批量删除，ids 为流水ID数组 */
export const deleteRecordBatch = (ids) => request.delete('/record/batch', { data: ids })
export const askRecord = (data) => request.post('/record/ask', data, { timeout: 90000 })
