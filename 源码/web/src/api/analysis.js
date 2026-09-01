import request from '../utils/request'

export const analysisReport = (params) => request.get('/analysis/report', { params })
