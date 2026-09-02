/**
 * 跨标签页认证事件。事件只表达“发生了什么”，真实身份始终重新向服务端确认。
 * BroadcastChannel 不可用时退回 storage 事件；两种载荷都不含用户信息或凭证。
 */
const CHANNEL_NAME = 'gjp-auth'
const STORAGE_KEY = 'gjp_auth_event'

export const AUTH_EVENT = Object.freeze({
  LOGIN: 'AUTH_LOGIN',
  LOGOUT: 'AUTH_LOGOUT',
  SWITCH: 'AUTH_SWITCH',
  EXPIRED: 'AUTH_EXPIRED'
})

const allowedEvents = new Set(Object.values(AUTH_EVENT))
const channel = typeof BroadcastChannel === 'undefined' ? null : new BroadcastChannel(CHANNEL_NAME)

function createEvent(type) {
  return {
    type,
    occurredAt: Date.now(),
    eventId: `${Date.now()}-${Math.random().toString(36).slice(2)}`
  }
}

export function publishAuthEvent(type) {
  if (!allowedEvents.has(type)) return
  const event = createEvent(type)
  if (channel) {
    channel.postMessage(event)
    return
  }
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(event))
    localStorage.removeItem(STORAGE_KEY)
  } catch (e) {
    // 隐私模式禁用 localStorage 时，同标签页仍由调用方完成状态清理。
  }
}

export function listenAuthEvents(handler) {
  const onChannelMessage = (event) => {
    if (allowedEvents.has(event.data?.type)) handler(event.data)
  }
  const onStorage = (event) => {
    if (event.key !== STORAGE_KEY || !event.newValue) return
    try {
      const data = JSON.parse(event.newValue)
      if (allowedEvents.has(data.type)) handler(data)
    } catch (e) {
      // 忽略其他脚本写入的损坏数据。
    }
  }

  if (channel) {
    channel.addEventListener('message', onChannelMessage)
  } else {
    window.addEventListener('storage', onStorage)
  }

  return () => {
    channel?.removeEventListener('message', onChannelMessage)
    window.removeEventListener('storage', onStorage)
  }
}
