import request from '../utils/request'

export const pageLog = (query) => request.post('/log/page', query)
export const logModuleStat = () => request.get('/log/module-stat')
export const logOptions = () => request.get('/log/options')
