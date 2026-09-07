import request from '../utils/request'

export const analysisReport = (params, signal) => request.get('/analysis/report', { params, signal, timeout: 20000, silentError: true })
export const enhanceAnalysis = (reportId, signal) => request.post('/analysis/enhance', { reportId }, { signal, timeout: 90000, silentError: true })
export const analysisConfig = () => request.get('/analysis/config')
