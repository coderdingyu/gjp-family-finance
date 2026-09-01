import request from '../utils/request'

export const assetSummary = () => request.get('/asset/summary')
export const listAsset = () => request.get('/asset/list')
export const addAsset = (data) => request.post('/asset', data)
export const updateAsset = (id, data) => request.put(`/asset/${id}`, data)
export const deleteAsset = (id) => request.delete(`/asset/${id}`)

export const listLoan = () => request.get('/asset/loan/list')
export const addLoan = (data) => request.post('/asset/loan', data)
export const updateLoan = (id, data) => request.put(`/asset/loan/${id}`, data)
export const deleteLoan = (id) => request.delete(`/asset/loan/${id}`)
