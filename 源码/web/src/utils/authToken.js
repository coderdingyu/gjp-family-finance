/**
 * 本标签页的身份令牌。
 *
 * 多账号并行登录的关键就在这里：token 存 sessionStorage，而 sessionStorage
 * 天生按标签页隔离，所以每个标签页可以各自持有一个身份。
 * 反过来，Cookie（JSESSIONID）是整个浏览器共用的，只靠它就只能有一个当前登录人。
 *
 * 后端拿 token 去 session 里的身份表查登录人，见 com.gjp.common.AuthSlots。
 */
const KEY = 'gjp_auth_token'

export function getToken() {
  try {
    return sessionStorage.getItem(KEY) || ''
  } catch (e) {
    // 隐私模式可能禁用 storage，此时退化为"刷新即掉线"，但不该直接报错白屏
    return ''
  }
}

export function setToken(token) {
  try {
    if (token) {
      sessionStorage.setItem(KEY, token)
    } else {
      sessionStorage.removeItem(KEY)
    }
  } catch (e) {
    // 同上，写不进去不影响当前这次会话继续用内存里的身份
  }
}

export function clearToken() {
  setToken('')
}
