/** 金额格式化：千分位 + 两位小数，null 显示 0.00 */
export function money(value) {
  const num = Number(value || 0)
  return num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** 带正负号的金额，用于结余展示 */
export function signedMoney(value) {
  const num = Number(value || 0)
  return (num > 0 ? '+' : '') + money(num)
}

/** 日期对象转 yyyy-MM-dd，后端 LocalDate 只认这个格式 */
export function toDateStr(date) {
  if (!date) return null
  if (typeof date === 'string') return date.slice(0, 10)
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

/** 今天的 yyyy-MM-dd */
export function today() {
  return toDateStr(new Date())
}

/** 当前年份 */
export function currentYear() {
  return new Date().getFullYear()
}

/** 当前年月 yyyy-MM */
export function currentMonth() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

/** 收支类型文字 */
export function typeText(type) {
  return Number(type) === 1 ? '收入' : '支出'
}

/** Soft UI 语义色，给 ECharts 等无法读 CSS 变量的地方用 */
export const TONE = {
  primary: '#4f46e5',
  income: '#10b981',
  expense: '#ec4899',
  balance: '#f59e0b',
  count: '#4f46e5'
}

/** 图表统一配色：与全局 CSS 变量保持一致 */
export const CHART_COLORS = [
  '#6366f1', '#ec4899', '#10b981', '#f59e0b', '#818cf8',
  '#f472b6', '#34d399', '#fbbf24', '#94a3b8', '#4f46e5',
  '#a78bfa', '#fb7185'
]
