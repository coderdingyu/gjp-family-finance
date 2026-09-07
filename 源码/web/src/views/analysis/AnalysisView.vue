<template>
  <div class="analysis-page">
    <section class="page-card toolbar">
      <div><h2>智能分析</h2><p class="text-light">看懂变化，核对依据，再决定下一步。</p></div>
      <div class="tools">
        <MemberScope v-model="memberId" @change="load()" />
        <el-date-picker v-model="range" type="daterange" start-placeholder="开始日期" end-placeholder="结束日期"
          value-format="YYYY-MM-DD" :disabled-date="futureDate" @change="load()" />
        <el-button :loading="loading" @click="load(true)">重新分析</el-button>
      </div>
    </section>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />
    <section v-if="loading" class="page-card"><el-skeleton :rows="5" animated /></section>
    <template v-if="report">
      <section class="page-card overview">
        <div class="overview-heading"><div><h3>{{ report.scopeLabel }} · 收支复盘</h3>
          <p class="text-light">{{ report.startDate }} 至 {{ report.endDate }} · {{ generatedAt }}</p></div>
          <el-tag :type="statusType">{{ statusLabel }}</el-tag>
        </div>
        <div class="metrics">
          <div><span>已记收入</span><strong>¥{{ money(report.totals.income) }}</strong></div>
          <div><span>已记支出</span><strong>¥{{ money(report.totals.expense) }}</strong></div>
          <div><span>账面结余</span><strong>¥{{ money(report.totals.balance) }}</strong></div>
          <div><span>流水 / 可比月份</span><strong>{{ report.quality.recordCount }} 笔 / {{ report.quality.comparableMonths }} 月</strong></div>
        </div>
        <p class="local-summary">{{ localSummary }}</p>
        <p class="text-light">商家填写覆盖率：{{ report.quality.merchantCoverage }}%（按支出笔数）；报告描述的是快照生成时的数据。</p>
      </section>

      <section v-if="report.quality.recordCount" class="page-card ai-panel">
        <div class="overview-heading"><h3>AI 重点与下一步</h3>
          <el-button v-if="report.aiConfigured" type="primary" :loading="enhancing"
            :disabled="report.aiStatus !== 'rules'" @click="enhance">{{ enhancing ? '正在整理，本地报告可继续查看' : '生成 AI 重点' }}</el-button>
        </div>
        <p role="status">{{ enhancing ? 'AI 正在基于这份快照选择重点，不会修改原始事实。' : report.aiMessage }}</p>
        <p v-if="report.aiConfigured && report.aiStatus === 'rules'" class="text-light">点击后将本次分析的汇总事实（可能含成员/分类/商家名称和金额）发送至已配置的 Dify 服务；不发送账单原件。</p>
        <p v-if="report.aiStatus === 'fallback'" class="text-light">可稍后点击“重新分析”再试；旧版工作流需重新导入 analysis-v2 配置。</p>
        <div v-for="insight in report.aiInsights" :key="insight.findingId" class="insight">
          <h4>{{ insight.title }}</h4><p>{{ insight.basis }}</p><p>{{ insight.suggestion }}</p>
          <el-button v-for="id in insight.evidenceIds" :key="id" size="small" text @click="focusEvidence(id)">查看依据 {{ id }}</el-button>
        </div>
      </section>

      <section class="page-card">
        <h3>数据质量与分析边界</h3>
        <ul><li v-for="notice in report.notices" :key="notice">{{ notice }}</li></ul>
        <el-button size="small" @click="router.push('/dedup')">核对疑似重复账单</el-button>
        <details class="month-detail"><summary>查看每月记录覆盖情况</summary>
          <el-table :data="report.quality.months" size="small">
            <el-table-column prop="ym" label="月份" width="100" />
            <el-table-column prop="recordCount" label="流水笔数" width="100" />
            <el-table-column prop="status" label="样本状态" />
          </el-table>
        </details>
      </section>

      <section v-if="priority.length" class="page-card">
        <h3>优先核对的 {{ priority.length }} 件事</h3>
        <AnalysisFinding v-for="item in priority" :key="item.findingId" :item="item" :can-manage="isOwner"
          @evidence="showEvidence" @budget="router.push('/member')" />
      </section>
      <section class="page-card">
        <h3>{{ priority.length ? '其余发现与参考信息' : '分析发现与参考信息' }}</h3>
        <el-empty v-if="!report.quality.recordCount" description="所选区间暂无流水，请先补充账单或调整日期。" />
        <AnalysisFinding v-for="item in remaining" :key="item.findingId" :item="item" :can-manage="isOwner"
          @evidence="showEvidence" @budget="router.push('/member')" />
      </section>
    </template>

    <section class="page-card">
      <details><summary>分析规则与解释方式</summary>
        <el-table :data="rules" size="small" stripe>
          <el-table-column prop="code" label="编号" width="70" />
          <el-table-column prop="logic" label="判定与限制" />
        </el-table>
        <p class="text-light">AI 只选择重点和解释方向。金额、姓名、比例及依据均从本地事实渲染，未通过证据校验的结果会回退本地报告。</p>
      </details>
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import MemberScope from '../../components/MemberScope.vue'
import AnalysisFinding from '../../components/AnalysisFinding.vue'
import { analysisReport, enhanceAnalysis } from '../../api/analysis'
import { createLatestRequestGate } from '../../utils/latestRequest.mjs'
import { currentUser } from '../../utils/auth'
import { toDateStr, money } from '../../utils/format'

const router = useRouter()
const range = ref([`${new Date().getFullYear()}-01-01`, toDateStr(new Date())])
const memberId = ref(null), report = ref(null), loading = ref(false), enhancing = ref(false), errorMessage = ref('')
const gate = createLatestRequestGate()
const isOwner = computed(() => currentUser.value?.role === 1)
const priority = computed(() => (report.value?.items || []).filter(i => ['danger', 'warning'].includes(i.level)).slice(0, 3))
const remaining = computed(() => (report.value?.items || []).filter(i => i.code !== 'A0' && !priority.value.some(p => p.findingId === i.findingId)))
const generatedAt = computed(() => report.value ? new Date(report.value.generatedAt).toLocaleString('zh-CN') : '')
const statusLabel = computed(() => enhancing.value ? 'AI 整理中' : ({ rules: '规则分析已完成', enhanced: 'AI 增强成功', fallback: 'AI 暂不可用' }[report.value?.aiStatus] || '规则分析'))
const statusType = computed(() => ({ enhanced: 'success', fallback: 'warning' }[report.value?.aiStatus] || 'info'))
const localSummary = computed(() => {
  if (!report.value) return ''
  if (!report.value.quality.recordCount) return '暂无可分析的流水，请调整日期或补充记录。'
  const count = report.value.items.filter(i => ['danger', 'warning'].includes(i.level)).length
  return count ? `发现 ${count} 项值得核对的变化或预算信号，先看下面优先展示的 ${Math.min(count, 3)} 项。` : '当前规则未发现显著变化或预算超限；这不等于数据完整或所有支出都合理。'
})
const rules = [
  { code: 'A1', logic: '至少三个完整且有支出的月份；峰值比其余同口径月均高出 30% 提示核对；分类归因使用同一组月份。' },
  { code: 'A2', logic: '仅比较相邻且有记录的完整月份；涨跌达到 20% 给出提示，零基数不计算比例。次数/均额分解不是因果判断。' },
  { code: 'A3', logic: '结余率按已记收入支出计算；负结余优先核对，低于 10% 提示；没有收入时不评价健康度。' },
  { code: 'A4', logic: '成员 ID + 月份唯一定位。支出超过当前预算提示；完整月使用率至少 80% 提示回看。历史月份不冒充历史预算。' },
  { code: 'A5–A8', logic: '分类、商家、片区、人情往来只作结构参考，不仅凭占比高认定浪费。商家占比分母是所有合格商家金额，不是榜单小计。' }
]
function futureDate(date) { return toDateStr(date) > toDateStr(new Date()) }
async function load(refresh = false) {
  const ticket = gate.next()
  report.value = null; enhancing.value = false; errorMessage.value = ''; loading.value = false
  if (!range.value || range.value.length !== 2) { errorMessage.value = '请选择完整的日期区间。'; return }
  loading.value = true
  try {
    const params = { startDate: range.value[0], endDate: range.value[1], refresh }
    if (memberId.value) params.memberId = memberId.value
    const result = await analysisReport(params, ticket.signal)
    if (ticket.current()) report.value = result
  } catch (error) {
    if (ticket.current() && error.code !== 'ERR_CANCELED') errorMessage.value = error.message || '分析失败，请重试。'
  } finally { if (ticket.current()) loading.value = false }
}
async function enhance() {
  if (!report.value || enhancing.value) return
  const id = report.value.reportId, ticket = gate.next()
  enhancing.value = true
  try {
    const result = await enhanceAnalysis(id, ticket.signal)
    if (ticket.current() && report.value?.reportId === id) report.value = result
  } catch (error) {
    if (ticket.current() && error.code !== 'ERR_CANCELED' && report.value?.reportId === id)
      report.value = { ...report.value, aiStatus: 'fallback', aiMessage: 'AI 请求未完成，已保留本地报告。可重新分析后再试。' }
  } finally { if (ticket.current()) enhancing.value = false }
}
function showEvidence(item) { router.push({ path: '/record', query: { ...item.recordFilters, fromAnalysis: '1' } }) }
function focusEvidence(id) { document.getElementById(`finding-${id}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' }) }
onMounted(() => load())
onBeforeUnmount(() => gate.cancel())
</script>

<style scoped>
.analysis-page { max-width: 1250px; margin: auto; }.page-card { margin-bottom: 18px; }
h2 { margin: 0; font-size: 22px; } h3 { margin: 0 0 12px; font-size: 17px; } p { line-height: 1.8; font-size: 14px; }
.toolbar, .overview-heading { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 14px; }
.tools { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; }
.tools :deep(.el-date-editor) { width: 250px; flex-grow: 0; }
.metrics { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin: 20px 0; }
.metrics div { background: #f4f8f5; border-radius: 8px; padding: 16px; }.metrics span { display: block; color: #617469; font-size: 13px; }
.metrics strong { display: block; font-size: 21px; color: #245e46; margin-top: 8px; overflow-wrap: anywhere; }
.local-summary { font-weight: 600; }.ai-panel { border-top: 3px solid #387e60; }
.insight { padding: 12px 16px; background: #f4f8f5; border-radius: 8px; margin-top: 10px; }.insight h4 { margin: 0; }
ul { padding-left: 20px; line-height: 1.9; color: #65736b; font-size: 13px; } summary { cursor: pointer; line-height: 1.8; }.month-detail { margin-top: 12px; }
@media (max-width: 760px) { .metrics { grid-template-columns: repeat(2, 1fr); } .tools { width: 100%; } }
@media (max-width: 420px) { .metrics { grid-template-columns: 1fr; } .metrics strong { font-size: 20px; } }
</style>
