import request from '../utils/request'

export const assetSummary = () => request.get('/asset/summary', { timeout: 25000 })
export const listAsset = () => request.get('/asset/list', { timeout: 25000 })
export const addAsset = (data) => request.post('/asset', data)
export const updateAsset = (id, data) => request.put(`/asset/${id}`, data)
export const deleteAsset = (id) => request.delete(`/asset/${id}`)

export const quoteAsset = (params) => request.get('/asset/quote', { params, timeout: 20000 })
export const estimateAsset = (params) => request.get('/asset/estimate', { params, timeout: 20000 })
export const usedCarPrice = (params) => request.get('/asset/used-car-price', { params, timeout: 20000 })
export const interestPreview = (params) => request.get('/asset/interest', { params })
export const listCarTree = () => request.get('/asset/car-tree', { timeout: 15000 })

export const listLoan = () => request.get('/asset/loan/list')
export const addLoan = (data) => request.post('/asset/loan', data)
export const updateLoan = (id, data) => request.put(`/asset/loan/${id}`, data)
export const deleteLoan = (id) => request.delete(`/asset/loan/${id}`)
