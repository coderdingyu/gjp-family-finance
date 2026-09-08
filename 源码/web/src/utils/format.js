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

/** 图表统一配色：与全局 CSS 变量保持一致 */
export const CHART_COLORS = [
  '#4f46e5', '#ec4899', '#10b981', '#f59e0b', '#6366f1',
  '#f472b6', '#34d399', '#fbbf24', '#94a3b8', '#a78bfa',
  '#22d3ee', '#fb7185'
]

/** 当前是否暗色主题（html.dark） */
export function isDarkTheme() {
  if (typeof document === 'undefined') return false
  return document.documentElement.classList.contains('dark')
}

/** ECharts 主题色：轴标签 / 图例 / 分割线 / 饼图描边 */
export function getChartTheme() {
  const dark = isDarkTheme()
  return {
    dark,
    text: dark ? '#cbd5e1' : '#64748b',
    muted: dark ? '#94a3b8' : '#909399',
    split: dark ? 'rgba(148, 163, 184, 0.22)' : 'rgba(148, 163, 184, 0.35)',
    axisLine: dark ? '#475569' : '#e2e8f0',
    pieBorder: dark ? '#1e293b' : '#ffffff'
  }
}

/**
 * 给 echarts option 注入暗色可读的 textStyle / legend / axis / splitLine。
 * 暗色下强制浅灰文字；亮色尽量保留原 option 已设颜色。
 */
export function withChartTheme(option) {
  if (!option || typeof option !== 'object') return option
  const t = getChartTheme()
  const out = { ...option }

  out.textStyle = { color: t.text, ...(option.textStyle || {}), ...(t.dark ? { color: t.text } : {}) }

  const patchTextStyle = (obj, fallback) => {
    if (!obj) return obj
    const ts = { ...(obj.textStyle || {}) }
    if (t.dark || !ts.color) ts.color = fallback
    return { ...obj, textStyle: ts }
  }

  if (option.legend) {
    if (Array.isArray(option.legend)) {
      out.legend = option.legend.map((l) => patchTextStyle(l, t.text))
    } else {
      out.legend = patchTextStyle(option.legend, t.text)
    }
  }

  if (option.title) {
    if (Array.isArray(option.title)) {
      out.title = option.title.map((ti) => patchTextStyle(ti, t.muted))
    } else {
      out.title = patchTextStyle(option.title, t.muted)
    }
  }

  const patchAxis = (axis) => {
    if (!axis) return axis
    const one = (a) => {
      if (!a || typeof a !== 'object') return a
      const axisLabel = { ...(a.axisLabel || {}) }
      if (t.dark || !axisLabel.color) axisLabel.color = t.text
      const nameTextStyle = { ...(a.nameTextStyle || {}) }
      if (t.dark || !nameTextStyle.color) nameTextStyle.color = t.muted
      const lineStyle = { color: t.axisLine, ...(a.axisLine?.lineStyle || {}) }
      if (t.dark) lineStyle.color = t.axisLine
      const splitLineStyle = { color: t.split, ...(a.splitLine?.lineStyle || {}) }
      if (t.dark) splitLineStyle.color = t.split
      return {
        ...a,
        axisLabel,
        nameTextStyle,
        axisLine: { ...(a.axisLine || {}), lineStyle },
        splitLine: { show: a.splitLine?.show !== false, ...(a.splitLine || {}), lineStyle: splitLineStyle }
      }
    }
    return Array.isArray(axis) ? axis.map(one) : one(axis)
  }

  if (option.xAxis) out.xAxis = patchAxis(option.xAxis)
  if (option.yAxis) out.yAxis = patchAxis(option.yAxis)

  if (option.series) {
    const list = Array.isArray(option.series) ? option.series : [option.series]
    const patched = list.map((s) => {
      if (!s || typeof s !== 'object') return s
      const next = { ...s }
      if (s.label) {
        const label = { ...s.label }
        if (t.dark || !label.color) label.color = t.text
        next.label = label
      }
      if (s.type === 'pie') {
        const itemStyle = { ...(s.itemStyle || {}) }
        const bc = itemStyle.borderColor
        if (t.dark && (!bc || bc === '#fff' || bc === '#ffffff' || bc === 'white')) {
          itemStyle.borderColor = t.pieBorder
        }
        next.itemStyle = itemStyle
        if (s.emphasis?.label) {
          next.emphasis = {
            ...s.emphasis,
            label: {
              ...s.emphasis.label,
              color: t.dark ? '#f8fafc' : s.emphasis.label.color || t.text
            }
          }
        }
      }
      return next
    })
    out.series = Array.isArray(option.series) ? patched : patched[0]
  }

  return out
}
