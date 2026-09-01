import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

/**
 * axios 统一实例。
 * 约定：后端返回 {code, msg, data}，code=0 才算成功。
 * 拦截器里直接把 data 解出来返回，所以业务代码里写 const list = await listMember() 就够了，
 * 不需要每处都判断 res.data.code。
 */
const request = axios.create({
  baseURL: '/api',
  timeout: 15000,
  // session 登录态靠 Cookie，必须带上凭证
  withCredentials: true
})

request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code === 0) {
      return res.data
    }
    if (res.code === 401) {
      // 登录过期：跳回登录页，不再弹重复的错误提示
      ElMessage.warning('登录已过期，请重新登录')
      router.replace('/login')
      return Promise.reject(new Error(res.msg))
    }
    ElMessage.error(res.msg || '操作失败')
    return Promise.reject(new Error(res.msg))
  },
  (error) => {
    ElMessage.error('网络异常，请确认后端服务已启动（http://localhost:8080）')
    return Promise.reject(error)
  }
)

export default request
