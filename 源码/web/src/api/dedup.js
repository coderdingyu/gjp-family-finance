import request from '../utils/request'

export const scanDuplicates = (params) => request.get('/dedup/scan', { params })
/** 用户勾选后删除，ids 为流水ID数组 */
export const deleteDuplicates = (ids) => request.delete('/dedup/records', { data: ids })
