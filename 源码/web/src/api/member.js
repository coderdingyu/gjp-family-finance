import request from '../utils/request'

export const listMember = () => request.get('/member/list')
export const addMember = (data) => request.post('/member', data)
export const updateMember = (id, data) => request.put(`/member/${id}`, data)
export const deleteMember = (id) => request.delete(`/member/${id}`)

// ---- 成员登录账号（仅户主可用）----
export const listAccounts = () => request.get('/member/accounts')
export const createAccount = (memberId, username, password) =>
  request.post(`/member/${memberId}/account`, { username, password })
export const resetAccountPassword = (userId, password) =>
  request.put(`/member/account/${userId}/password`, { password })
export const toggleAccountStatus = (userId, status) =>
  request.put(`/member/account/${userId}/status`, { status })
