import { currentUser as fetchCurrent } from '../api/auth'
import { clearUser, currentUser, ROLE, setUser } from './auth'
import { AUTH_EVENT, listenAuthEvents, publishAuthEvent } from './authSync'

let currentRequest = null
let syncStarted = false

export function homeOf(role) {
  return role === ROLE.ADMIN ? '/admin' : '/home'
}

function identityOf(user) {
  if (!user?.userId) return ''
  return [user.userId, user.familyId, user.memberId, user.role].join(':')
}

/** 以 /auth/current 为唯一真相，并合并同一时刻的重复校验请求。 */
export function verifyCurrentUser({ broadcastExpiry = false } = {}) {
  if (currentRequest) return currentRequest

  const previous = currentUser.value
  currentRequest = fetchCurrent()
    .then((serverUser) => {
      const changed = !!previous.userId && identityOf(previous) !== identityOf(serverUser)
      if (changed) clearUser()
      setUser(serverUser)
      return { user: serverUser, changed }
    })
    .catch((error) => {
      if (!error.authUnauthorized) throw error
      const hadUser = !!previous.userId
      clearUser()
      if (hadUser && broadcastExpiry) publishAuthEvent(AUTH_EVENT.EXPIRED)
      return { user: null, changed: hadUser }
    })
    .finally(() => {
      currentRequest = null
    })

  return currentRequest
}

function replaceWith(path, force = false) {
  const target = new URL(path, window.location.origin)
  if (force || window.location.pathname !== target.pathname || window.location.search !== target.search) {
    window.location.replace(target.pathname + target.search)
  }
}

/**
 * 全局监听其他标签页和重新聚焦事件。身份有变化时整页重载，销毁旧组件、轮询和请求。
 */
export function startAuthSessionSync() {
  if (syncStarted) return
  syncStarted = true

  listenAuthEvents(async ({ type }) => {
    if ([AUTH_EVENT.LOGOUT, AUTH_EVENT.SWITCH, AUTH_EVENT.EXPIRED].includes(type)) {
      clearUser()
      replaceWith(type === AUTH_EVENT.SWITCH ? '/login?switch=1' : '/login')
      return
    }

    if (type === AUTH_EVENT.LOGIN) {
      try {
        const { user } = await verifyCurrentUser()
        if (user) replaceWith(homeOf(user.role), true)
      } catch (e) {
        // 网络故障时保留当前页面；恢复焦点后还会再次向服务端校准。
      }
    }
  })

  window.addEventListener('focus', async () => {
    try {
      const { user, changed } = await verifyCurrentUser({ broadcastExpiry: true })
      if (!user) {
        replaceWith('/login')
      } else if (changed) {
        replaceWith(homeOf(user.role), true)
      }
    } catch (e) {
      // 不能因瞬时网络故障把仍有效的本地展示状态误判为退出。
    }
  })
}
