function validDate(value) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false
  const date = new Date(`${value}T00:00:00Z`)
  return !Number.isNaN(date.valueOf()) && date.toISOString().slice(0, 10) === value
}

// URL 只是筛选入口，绝不接收 familyId；最终权限仍由后端收敛。
export function parseAnalysisEvidence(query) {
  if (query.fromAnalysis !== '1' || !validDate(query.startDate) || !validDate(query.endDate)
    || query.startDate > query.endDate) return null
  const result = { startDate: query.startDate, endDate: query.endDate }
  for (const key of ['memberId', 'categoryId']) {
    if (typeof query[key] === 'string' && /^[1-9]\d*$/.test(query[key])) {
      const value = Number(query[key])
      if (Number.isSafeInteger(value)) result[key] = value
    }
  }
  if (['1', '2'].includes(query.type)) result.type = Number(query.type)
  if (['0', '1'].includes(query.isGift)) result.isGift = Number(query.isGift)
  if (typeof query.area === 'string' && query.area.length <= 100) result.area = query.area
  return result
}
