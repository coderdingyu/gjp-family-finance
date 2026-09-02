import axios from 'axios'
import { ElMessage } from 'element-plus'
import { clearUser } from './auth'
import { clearToken, getToken } from './authToken'

/**
 * axios 统一实例。
 * 约定：后端返回 {code, msg, data}，code=0 才算成功。
 * 拦截器里直接把 data 解出来返回，所以业务代码里写 const list = await listMember() 就够了，
 * 不需要每处都判断 res.data.code。
 */
const request = axios.create({
  baseURL: '/api',
  timeout: 15000,
  // 身份表挂在服务端 session 上，Cookie 仍要带；具体用表里哪个身份由下面的 token 头指定
  withCredentials: true
})

request.interceptors.request.use((config) => {
  // 每个标签页带自己的 token，服务端据此区分同一浏览器里的多个登录账号
  const token = getToken()
  if (token) {
    config.headers['X-Auth-Token'] = token
  }
  return config
})

let authRedirecting = false

function unauthorized(message, config = {}) {
  const error = new Error(message || '未登录或登录已过期')
  error.authUnauthorized = true

  // /auth/current 的主动校准由调用方决定是否跳转，避免首次打开登录页也提示“已过期”。
  if (config.skipAuthRedirect) return Promise.reject(error)

  // 只清本标签页的身份。别的标签页登着别的账号，与这次掉线无关，不要去动它们。
  clearToken()
  clearUser()
  if (!authRedirecting) {
    authRedirecting = true
    ElMessage.warning('登录已过期，请重新登录')
    window.location.replace('/login')
  }
  return Promise.reject(error)
}

request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code === 0) {
      return res.data
    }
    if (res.code === 401) {
      return unauthorized(res.msg, response.config)
    }
    ElMessage.error(res.msg || '操作失败')
    return Promise.reject(new Error(res.msg))
  },
  (error) => {
    if (error.response?.status === 401) {
      return unauthorized(error.response.data?.msg, error.config)
    }
    ElMessage.error('网络异常，请确认后端服务已启动（http://localhost:8080）')
    return Promise.reject(error)
  }
)

export default request
