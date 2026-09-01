import request from '../utils/request'

export const listMember = () => request.get('/member/list')
export const addMember = (data) => request.post('/member', data)
export const updateMember = (id, data) => request.put(`/member/${id}`, data)
export const deleteMember = (id) => request.delete(`/member/${id}`)
