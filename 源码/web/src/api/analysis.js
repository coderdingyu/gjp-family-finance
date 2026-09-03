import request from '../utils/request'

export const analysisReport = (params) => request.get('/analysis/report', { params, timeout: 90000 })
export const analysisConfig = () => request.get('/analysis/config')
