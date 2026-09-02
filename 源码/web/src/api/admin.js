import request from '../utils/request'

export const adminOverview = () => request.get('/admin/overview')
export const adminFamilies = () => request.get('/admin/families')
export const adminUsers = (params) => request.get('/admin/users', { params })
export const adminResetPassword = (userId, password) =>
  request.put(`/admin/users/${userId}/password`, { password })
export const adminToggleStatus = (userId, status) =>
  request.put(`/admin/users/${userId}/status`, { status })
export const adminChangeOwnPassword = (oldPassword, newPassword) =>
  request.put('/admin/password', { oldPassword, newPassword })
