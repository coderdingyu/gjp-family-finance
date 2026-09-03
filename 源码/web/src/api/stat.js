import request from '../utils/request'

export const dashboard = (params) => request.get('/stat/dashboard', { params })
export const personalBoard = () => request.get('/stat/personal')
export const overview = (params) => request.get('/stat/overview', { params })
export const trend = (params) => request.get('/stat/trend', { params })
export const categoryStat = (params) => request.get('/stat/category', { params })
export const subCategoryStat = (params) => request.get('/stat/sub-category', { params })
export const memberStat = (params) => request.get('/stat/member', { params })
export const merchantRank = (params) => request.get('/stat/merchant', { params })
export const areaStat = (params) => request.get('/stat/area', { params })
export const payMethodStat = (params) => request.get('/stat/pay-method', { params })
export const budgetStat = (ym) => request.get('/stat/budget', { params: { ym } })
