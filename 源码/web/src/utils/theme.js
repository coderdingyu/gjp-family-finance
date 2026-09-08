/** Soft UI 主题：light / dark，偏好持久化到 localStorage */

const STORAGE_KEY = 'gjp-theme'
const DARK = 'dark'
const LIGHT = 'light'

export function getStoredTheme() {
  try {
    const v = localStorage.getItem(STORAGE_KEY)
    return v === DARK ? DARK : LIGHT
  } catch {
    return LIGHT
  }
}

export function applyTheme(theme) {
  const next = theme === DARK ? DARK : LIGHT
  const root = document.documentElement
  if (next === DARK) {
    root.classList.add('dark')
    root.setAttribute('data-theme', DARK)
  } else {
    root.classList.remove('dark')
    root.setAttribute('data-theme', LIGHT)
  }
  try {
    localStorage.setItem(STORAGE_KEY, next)
  } catch {
    /* ignore */
  }
  try {
    window.dispatchEvent(new CustomEvent('gjp-theme-change', { detail: { theme: next } }))
  } catch {
    /* ignore */
  }
  return next
}

export function toggleTheme() {
  const cur = document.documentElement.classList.contains('dark') ? DARK : LIGHT
  return applyTheme(cur === DARK ? LIGHT : DARK)
}

/** 尽早应用，减少首屏闪白/闪黑 */
export function initTheme() {
  return applyTheme(getStoredTheme())
}
