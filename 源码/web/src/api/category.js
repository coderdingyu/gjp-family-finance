import request from '../utils/request'

export const listCategory = (type) => request.get('/category/list', { params: { type } })
export const treeCategory = (type) => request.get('/category/tree', { params: { type } })
export const addCategory = (data) => request.post('/category', data)
export const updateCategory = (id, data) => request.put(`/category/${id}`, data)
export const deleteCategory = (id) => request.delete(`/category/${id}`)
