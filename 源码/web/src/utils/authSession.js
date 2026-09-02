import { currentUser as fetchCurrent } from '../api/auth'
import { clearUser, currentUser, ROLE, setUser } from './auth'
import { clearToken, getToken } from './authToken'

/**
 * 本标签页登录态的校准。
 *
 * 这里刻意**不做**跨标签页同步：身份由本标签页的 token 决定（见 authToken.js），
 * 每个标签页可以登着不同账号，一个标签页登录或退出不该影响别的标签页。
 * 也正因为身份绑在标签页上，不会再出现"服务端已切号、本页还挂着旧身份"的错位，
 * 那种错位原本要靠广播强制刷新来救，现在从结构上就不存在了。
 */

let currentRequest = null
let syncStarted = false

export function homeOf(role) {
  return role === ROLE.ADMIN ? '/admin' : '/home'
}

function identityOf(user) {
  if (!user?.userId) return ''
  return [user.userId, user.familyId, user.memberId, user.role].join(':')
}

/** 丢掉本标签页的身份。不调后端退出接口，因此不会影响其他标签页。 */
export function resetTabIdentity() {
  clearToken()
  clearUser()
}

/** 以 /auth/current 为唯一真相，并合并同一时刻的重复校验请求。 */
export function verifyCurrentUser() {
  if (currentRequest) return currentRequest

  const previous = currentUser.value
  // 没有 token 就没有身份可校准，省掉一次必然 401 的请求
  if (!getToken()) {
    if (previous.userId) clearUser()
    return Promise.resolve({ user: null, changed: !!previous.userId })
  }

  currentRequest = fetchCurrent()
    .then((serverUser) => {
      const changed = !!previous.userId && identityOf(previous) !== identityOf(serverUser)
      setUser(serverUser)
      return { user: serverUser, changed }
    })
    .catch((error) => {
      if (!error.authUnauthorized) throw error
      const hadUser = !!previous.userId
      resetTabIdentity()
      return { user: null, changed: hadUser }
    })
    .finally(() => {
      currentRequest = null
    })

  return currentRequest
}

function replaceWith(path) {
  const target = new URL(path, window.location.origin)
  if (window.location.pathname !== target.pathname || window.location.search !== target.search) {
    window.location.replace(target.pathname + target.search)
  }
}

/**
 * 窗口重新获得焦点时向服务端校准一次本标签页的身份。
 * 主要用于捕捉"离开期间 session 超时了"或"账号被户主禁用了"，此时送回登录页。
 */
export function startAuthSessionSync() {
  if (syncStarted) return
  syncStarted = true

  window.addEventListener('focus', async () => {
    if (!getToken()) return
    try {
      const { user } = await verifyCurrentUser()
      if (!user) replaceWith('/login')
    } catch (e) {
      // 不能因瞬时网络故障把仍有效的本地展示状态误判为退出。
    }
  })
}
