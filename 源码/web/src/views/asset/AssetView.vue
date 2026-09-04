<template>
  <div v-loading="loading">
    <!-- 净资产总览 -->
    <el-row :gutter="14" class="cards">
      <el-col :md="6" :sm="12">
        <StatCard label="资产合计" :value="summary.totalAsset" color="var(--gjp-income)"
                  :sub="`共 ${summary.assetCount || 0} 项资产`" />
      </el-col>
      <el-col :md="6" :sm="12">
        <StatCard label="贷款剩余本金" :value="summary.totalLoanRemain" color="var(--gjp-expense)"
                  :sub="`共 ${summary.loanCount || 0} 笔贷款`" />
      </el-col>
      <el-col :md="6" :sm="12">
        <StatCard label="净资产" :value="summary.netAsset"
                  :color="Number(summary.netAsset) >= 0 ? 'var(--gjp-balance)' : 'var(--gjp-expense)'"
                  sub="净资产 = 资产合计 − 贷款剩余本金" />
      </el-col>
      <el-col :md="6" :sm="12">
        <StatCard label="每月还款压力" :value="summary.monthlyPayTotal" color="var(--gjp-balance)"
                  sub="在还贷款的月供合计" />
      </el-col>
    </el-row>

    <el-row :gutter="14">
      <el-col :md="16">
        <div class="page-card">
          <div class="bar">
            <h3 class="card-title" style="margin: 0">家庭资产</h3>
            <el-button type="primary" size="small" :icon="Plus" @click="openAsset()">新增资产</el-button>
          </div>
          <el-table :data="assets" border stripe size="small" empty-text="还没有登记资产">
            <el-table-column prop="assetName" label="名称" min-width="110" show-overflow-tooltip />
            <el-table-column label="类型" width="88" align="center">
              <template #default="{ row }">
                <el-tag size="small" effect="plain">{{ typeLabel(row.assetType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="当前价值" width="124" align="right">
              <template #default="{ row }">
                <div>¥{{ money(row.amount) }}</div>
                <div v-if="row.valueSource && row.valueSource !== 'stored'" class="text-light">
                  {{ sourceLabel(row.valueSource) }}
                </div>
              </template>
            </el-table-column>
            <el-table-column label="取得成本" width="110" align="right">
              <template #default="{ row }">
                <span v-if="row.cost">¥{{ money(row.cost) }}</span>
                <span v-else class="text-light">—</span>
              </template>
            </el-table-column>
            <el-table-column label="盈亏" width="110" align="right">
              <template #default="{ row }">
                <span v-if="pnlOf(row) != null" :class="pnlOf(row) >= 0 ? 'amount-income' : 'amount-expense'">
                  {{ pnlOf(row) >= 0 ? '+' : '-' }}{{ money(Math.abs(pnlOf(row))) }}
                </span>
                <span v-else class="text-light">—</span>
              </template>
            </el-table-column>
            <el-table-column prop="buyDate" label="取得日期" width="96" />
            <el-table-column label="说明" min-width="160">
              <template #default="{ row }">
                <span class="text-light">{{ row.estimateNote || row.remainLabel || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="88" align="center" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openAsset(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="onDeleteAsset(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>
      <el-col :md="8">
        <div class="page-card">
          <h3 class="card-title">资产构成</h3>
          <EChart :option="pieOption" :empty="!summary.composition?.length" height="280px"
                  empty-text="还没有登记资产" />
        </div>
      </el-col>
    </el-row>

    <div class="page-card">
      <div class="bar">
        <h3 class="card-title" style="margin: 0">家庭贷款</h3>
        <el-button type="primary" size="small" :icon="Plus" @click="openLoan()">新增贷款</el-button>
      </div>
      <el-table :data="loans" border stripe size="small" empty-text="还没有登记贷款">
        <el-table-column prop="loanName" label="名称" min-width="130" show-overflow-tooltip />
        <el-table-column label="类型" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" type="warning">{{ row.loanType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="贷款总额" width="122" align="right">
          <template #default="{ row }">¥{{ money(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="月供" width="110" align="right">
          <template #default="{ row }">¥{{ money(row.monthlyPayment) }}</template>
        </el-table-column>
        <el-table-column label="期数" width="112" align="center">
          <template #default="{ row }">{{ row.paidMonths }} / {{ row.totalMonths }}</template>
        </el-table-column>
        <el-table-column label="还款进度" min-width="150">
          <template #default="{ row }">
            <el-progress :percentage="Number(row.progress)" :stroke-width="12" :text-inside="true" />
          </template>
        </el-table-column>
        <el-table-column label="剩余本金" width="122" align="right">
          <template #default="{ row }">
            <span class="amount-expense">¥{{ money(row.remainAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="剩余期数" width="86" align="center">
          <template #default="{ row }">{{ row.remainMonths }} 期</template>
        </el-table-column>
        <el-table-column label="操作" width="106" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openLoan(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="onDeleteLoan(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <p class="text-light" style="margin: 10px 0 0">
        说明：剩余本金按"剩余期数 × 月供"估算，未拆分等额本息中的利息部分，用于家庭记账场景已足够。
      </p>
    </div>

    <!-- 资产表单 -->
    <el-dialog v-model="assetDialog" :title="assetForm.id ? '编辑资产' : '新增资产'" width="560px" destroy-on-close>
      <el-form ref="assetRef" :model="assetForm" :rules="assetRules" label-width="100px">
        <el-form-item label="资产名称" prop="assetName">
          <el-input v-model="assetForm.assetName" placeholder="如：城东住房" maxlength="100" />
        </el-form-item>
        <el-form-item label="资产类型" prop="assetType">
          <el-select v-model="assetForm.assetType" placeholder="请选择" style="width: 100%">
            <el-option v-for="t in assetTypes" :key="t" :label="typeLabel(t)" :value="t" />
          </el-select>
        </el-form-item>

        <template v-if="assetForm.assetType === '股票' || assetForm.assetType === '基金'">
          <el-form-item :label="assetForm.assetType === '基金' ? '基金代码' : '股票代码'">
            <el-input v-model="assetForm.symbol" maxlength="32"
                      :placeholder="assetForm.assetType === '基金' ? '如 110022' : '如 600519 或 sh600519'" />
          </el-form-item>
          <el-form-item label="持仓数量">
            <el-input-number v-model="assetForm.shares" :min="0" :precision="4" :step="1"
                             controls-position="right" style="width: 100%" />
          </el-form-item>
          <el-form-item>
            <el-button size="small" :loading="quoting" @click="onQuote">查行情</el-button>
            <span class="text-light" style="margin-left: 8px">保存后按行情计算；查不到时仍用下方登记价值</span>
          </el-form-item>
        </template>

        <template v-else-if="assetForm.assetType === '存款'">
          <el-form-item label="年利率%">
            <el-input-number v-model="assetForm.annualRate" :min="0" :max="100" :precision="4" :step="0.1"
                             controls-position="right" style="width: 100%" />
          </el-form-item>
          <el-form-item label="存期(月)">
            <el-input-number v-model="assetForm.termMonths" :min="0" :max="1200" :step="1"
                             controls-position="right" style="width: 100%" />
          </el-form-item>
          <el-form-item label="计息方式">
            <el-select v-model="assetForm.interestMethod" placeholder="请选择" style="width: 100%">
              <el-option v-for="m in interestMethods" :key="m.value" :label="m.label" :value="m.value" />
            </el-select>
          </el-form-item>
          <div v-if="depositHint" class="text-light" style="margin: -6px 0 12px 100px">{{ depositHint }}</div>
        </template>

        <template v-else-if="assetForm.assetType === '车辆'">
          <el-form-item label="车型">
            <el-cascader v-model="carPath" :options="carTree" filterable clearable
                         :props="{ checkStrictly: true, expandTrigger: 'hover' }"
                         placeholder="品牌 / 车系 / 年款" style="width: 100%"
                         @change="onCarPathChange" />
            <div class="text-light">从目录选择。已有「{{ assetForm.carModel || '未选' }}」</div>
          </el-form-item>
          <el-form-item label="城市">
            <el-input v-model="assetForm.city" placeholder="如：北京" maxlength="50" />
          </el-form-item>
          <el-form-item label="年份">
            <el-input-number v-model="assetForm.modelYear" :min="1980" :max="2099" :step="1"
                             controls-position="right" style="width: 100%" />
          </el-form-item>
          <el-form-item label="里程(km)">
            <el-input-number v-model="assetForm.mileageKm" :min="0" :step="1000"
                             controls-position="right" style="width: 100%" />
          </el-form-item>
          <el-form-item>
            <el-button size="small" :loading="quoting" @click="onEstimate">估残值</el-button>
            <span class="text-light" style="margin-left: 8px">查管家婆残值接口：指导价按车龄折；挂牌能通时用挂牌中位</span>
          </el-form-item>
        </template>

        <template v-else-if="assetForm.assetType === '房产'">
          <el-form-item label="城市">
            <el-input v-model="assetForm.city" placeholder="如：北京" maxlength="50" />
          </el-form-item>
          <el-form-item label="小区">
            <el-input v-model="assetForm.community" placeholder="如：回龙观" maxlength="100" />
          </el-form-item>
          <el-form-item label="面积㎡">
            <el-input-number v-model="assetForm.areaSqm" :min="0" :precision="2" :step="1"
                             controls-position="right" style="width: 100%" />
          </el-form-item>
          <el-form-item>
            <el-button size="small" :loading="quoting" @click="onEstimate">估市值</el-button>
            <span class="text-light" style="margin-left: 8px">挂牌估值，仅供参考</span>
          </el-form-item>
        </template>

        <el-form-item :label="amountLabel" prop="amount">
          <el-input-number v-model="assetForm.amount" :min="0" :precision="2" :step="10000"
                           controls-position="right" style="width: 100%" />
          <div class="text-light">{{ amountHint }}</div>
        </el-form-item>
        <el-form-item label="取得成本">
          <el-input-number v-model="assetForm.cost" :min="0" :precision="2" :step="10000"
                           controls-position="right" style="width: 100%" />
          <div class="text-light">填写后可自动计算盈亏</div>
        </el-form-item>
        <el-form-item :label="assetForm.assetType === '存款' ? '起息日期' : '取得日期'">
          <el-date-picker v-model="assetForm.buyDate" type="date" value-format="YYYY-MM-DD"
                          :disabled-date="(d) => d > new Date()" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="assetForm.remark" type="textarea" :rows="2" maxlength="255" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assetDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveAsset">保存</el-button>
      </template>
    </el-dialog>

    <!-- 贷款表单 -->
    <el-dialog v-model="loanDialog" :title="loanForm.id ? '编辑贷款' : '新增贷款'" width="460px" destroy-on-close>
      <el-form ref="loanRef" :model="loanForm" :rules="loanRules" label-width="98px">
        <el-form-item label="贷款名称" prop="loanName">
          <el-input v-model="loanForm.loanName" placeholder="如：城东住房商业贷款" maxlength="100" />
        </el-form-item>
        <el-form-item label="贷款类型" prop="loanType">
          <el-select v-model="loanForm.loanType" placeholder="请选择" style="width: 100%">
            <el-option v-for="t in loanTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="贷款总额" prop="totalAmount">
          <el-input-number v-model="loanForm.totalAmount" :min="0.01" :precision="2" :step="10000"
                           controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="每月还款额" prop="monthlyPayment">
          <el-input-number v-model="loanForm.monthlyPayment" :min="0.01" :precision="2" :step="100"
                           controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="总期数（月）" prop="totalMonths">
          <el-input-number v-model="loanForm.totalMonths" :min="1" :max="600" controls-position="right"
                           style="width: 100%" />
        </el-form-item>
        <el-form-item label="起始还款日">
          <el-date-picker v-model="loanForm.startDate" type="date" value-format="YYYY-MM-DD"
                          style="width: 100%" />
          <div class="text-light">填写后已还期数按今天自动计算，每天打开页面即更新</div>
        </el-form-item>
        <el-form-item label="已还期数" prop="paidMonths">
          <el-input-number v-model="loanForm.paidMonths" :min="0" :max="loanForm.totalMonths || 600"
                           :disabled="!!loanForm.startDate"
                           controls-position="right" style="width: 100%" />
          <div v-if="loanForm.startDate" class="text-light">已按起始还款日自动计算</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="loanDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveLoan">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import EChart from '../../components/EChart.vue'
import StatCard from '../../components/StatCard.vue'
import {
  addAsset, addLoan, assetSummary, deleteAsset, deleteLoan, estimateAsset, usedCarPrice,
  listAsset, listCarTree, listLoan, quoteAsset, updateAsset, updateLoan
} from '../../api/asset'
import { CHART_COLORS, money } from '../../utils/format'

const assetTypes = ['房产', '车辆', '存款', '股票', '基金', '其他']
const loanTypes = ['房贷', '车贷', '消费贷']
const interestMethods = [
  { value: 'simple', label: '单利' },
  { value: 'compound_year', label: '年复利(利滚利)' },
  { value: 'compound_month', label: '月复利' }
]

const loading = ref(false)
const saving = ref(false)
const quoting = ref(false)
const carTree = ref([])
const carPath = ref([])
const summary = ref({})
const assets = ref([])
const loans = ref([])

const assetDialog = ref(false)
const assetRef = ref()
const assetForm = ref(emptyAsset())
const assetRules = {
  assetName: [{ required: true, message: '请输入资产名称', trigger: 'blur' }],
  assetType: [{ required: true, message: '请选择资产类型', trigger: 'change' }],
  amount: [{ required: true, message: '请输入当前价值', trigger: 'blur' }]
}

const loanDialog = ref(false)
const loanRef = ref()
const loanForm = ref(emptyLoan())
const loanRules = {
  loanName: [{ required: true, message: '请输入贷款名称', trigger: 'blur' }],
  loanType: [{ required: true, message: '请选择贷款类型', trigger: 'change' }],
  totalAmount: [{ required: true, message: '请输入贷款总额', trigger: 'blur' }],
  monthlyPayment: [{ required: true, message: '请输入每月还款额', trigger: 'blur' }],
  totalMonths: [{ required: true, message: '请输入总期数', trigger: 'blur' }]
}

function typeLabel(t) {
  return t === '存款' ? '存款/理财' : t
}

function sourceLabel(src) {
  if (src === 'quote') return '行情'
  if (src === 'interest') return '利息'
  if (src === 'listing') return '估值'
  return ''
}

function pnlOf(row) {
  if (row.pnl != null && row.pnl !== '') return Number(row.pnl)
  if (row.cost == null || row.cost === '') return null
  return Number(row.amount) - Number(row.cost)
}

function emptyAsset() {
  return {
    id: null, assetName: '', assetType: '', amount: 0, cost: null, buyDate: null, remark: '',
    symbol: '', shares: null, annualRate: null, termMonths: null, interestMethod: 'simple',
    carModel: '', city: '', community: '', areaSqm: null, mileageKm: null, modelYear: null
  }
}

function emptyLoan() {
  return {
    id: null, loanName: '', loanType: '', totalAmount: 0,
    monthlyPayment: 0, totalMonths: 240, paidMonths: 0, startDate: null
  }
}

const amountLabel = computed(() => (assetForm.value.assetType === '存款' ? '本金' : '当前价值'))
const amountHint = computed(() => {
  const t = assetForm.value.assetType
  if (t === '股票' || t === '基金') return '登记价值；有代码和持仓时列表按行情显示'
  if (t === '存款') return '本金。列表显示本金+利息'
  if (t === '车辆' || t === '房产') return '登记价值。有估值时列表显示估值/仅供参考'
  return ''
})

const depositHint = computed(() => {
  const f = assetForm.value
  if (f.assetType !== '存款') return ''
  const p = Number(f.amount || 0)
  const r = Number(f.annualRate || 0)
  if (!p || !r || !f.interestMethod || !f.buyDate) return ''
  const start = parseYmd(f.buyDate)
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  let asOf = today
  let remainLabel = ''
  if (f.termMonths) {
    const end = addMonths(start, Number(f.termMonths))
    if (today > end) asOf = end
    const remain = daysBetween(today, end)
    remainLabel = remain <= 0 ? '已可支取' : `还有 ${remain} 天到期`
  }
  const daysHeld = Math.max(0, daysBetween(start, asOf))
  const rate = r / 100
  let profit = 0
  if (f.interestMethod === 'simple') {
    profit = p * rate * (daysHeld / 365)
  } else if (f.interestMethod === 'compound_year') {
    profit = p * (Math.pow(1 + rate, daysHeld / 365) - 1)
  } else {
    profit = p * (Math.pow(1 + rate / 12, daysHeld / 30) - 1)
  }
  const now = p + profit
  return `预估利息 ¥${money(profit)}，本息 ¥${money(now)}${remainLabel ? '，' + remainLabel : ''}（仅供参考）`
})

function parseYmd(s) {
  const [y, m, d] = s.split('-').map(Number)
  return new Date(y, m - 1, d)
}
function addMonths(d, months) {
  return new Date(d.getFullYear(), d.getMonth() + months, d.getDate())
}
function daysBetween(a, b) {
  const ua = Date.UTC(a.getFullYear(), a.getMonth(), a.getDate())
  const ub = Date.UTC(b.getFullYear(), b.getMonth(), b.getDate())
  return Math.round((ub - ua) / 86400000)
}

const REFRESH_MS = 10 * 60 * 1000
let refreshTimer = null
onMounted(() => {
  load()
  loadCarTree()
  refreshTimer = setInterval(() => load(true), REFRESH_MS)
})
onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
})

async function loadCarTree() {
  try {
    carTree.value = await listCarTree() || []
  } catch {
    carTree.value = []
  }
}

function onCarPathChange(val) {
  const path = Array.isArray(val) ? val.filter(Boolean) : []
  assetForm.value.carModel = path.join(' / ')
  const last = path[path.length - 1]
  const m = typeof last === 'string' ? last.match(/^(\d{4})款$/) : null
  if (m) assetForm.value.modelYear = Number(m[1])
}

function pathFromModel(model) {
  if (!model) return []
  if (model.includes(' / ')) return model.split(' / ').map((x) => x.trim()).filter(Boolean)
  const raw = model.replace(/\s+/g, '').toLowerCase()
  for (const b of carTree.value) {
    const bn = String(b.value)
    for (const ser of b.children || []) {
      const sn = String(ser.value)
      const packed = (bn + sn).replace(/\s+/g, '').toLowerCase()
      if (raw.includes(packed) || (raw.includes(bn.toLowerCase()) && raw.includes(sn.replace(/\s+/g, '').toLowerCase()))) {
        return [bn, sn]
      }
    }
  }
  return []
}

async function load(silent) {
  if (!silent) loading.value = true
  try {
    const [s, a, l] = await Promise.all([assetSummary(), listAsset(), listLoan()])
    summary.value = s
    assets.value = a
    loans.value = l
  } finally {
    if (!silent) loading.value = false
  }
}

const pieOption = computed(() => {
  const rows = summary.value.composition || []
  return {
    color: CHART_COLORS,
    tooltip: { trigger: 'item', formatter: (p) => `${p.name}<br/>¥${money(p.value)}（${p.percent}%）` },
    legend: { bottom: 0, itemWidth: 10, itemHeight: 10 },
    series: [
      {
        type: 'pie',
        radius: ['40%', '64%'],
        center: ['50%', '45%'],
        itemStyle: { borderColor: '#fff', borderWidth: 2 },
        label: { formatter: '{b}\n{d}%', fontSize: 11 },
        labelLine: { length: 8, length2: 8 },
        data: rows.map((r) => ({ name: r.name, value: Number(r.amount) }))
      }
    ]
  }
})

function numOrNull(v) {
  if (v === null || v === undefined || v === '') return null
  const n = Number(v)
  return Number.isFinite(n) ? n : null
}

function openAsset(row) {
  assetForm.value = row
    ? {
        id: row.id,
        assetName: row.assetName,
        assetType: row.assetType,
        amount: Number(row.storedAmount != null ? row.storedAmount : row.amount),
        cost: row.cost === null || row.cost === undefined ? null : Number(row.cost),
        buyDate: row.buyDate,
        remark: row.remark,
        symbol: row.symbol || '',
        shares: numOrNull(row.shares),
        annualRate: numOrNull(row.annualRate),
        termMonths: numOrNull(row.termMonths),
        interestMethod: row.interestMethod || 'simple',
        carModel: row.carModel || '',
        city: row.city || '',
        community: row.community || '',
        areaSqm: numOrNull(row.areaSqm),
        mileageKm: numOrNull(row.mileageKm),
        modelYear: numOrNull(row.modelYear)
      }
    : emptyAsset()
  carPath.value = pathFromModel(assetForm.value.carModel)
  assetDialog.value = true
}

async function onQuote() {
  const f = assetForm.value
  if (!f.symbol) {
    ElMessage.warning('请先填写代码')
    return
  }
  quoting.value = true
  try {
    const data = await quoteAsset({ type: f.assetType, symbol: f.symbol, shares: f.shares })
    if (!data?.ok) {
      ElMessage.warning(data?.reason || '行情暂不可用')
      return
    }
    if (data.marketValue != null) {
      assetForm.value.amount = Number(data.marketValue)
    }
    ElMessage.success((data.name ? data.name + ' ' : '') + '最新价 ' + data.lastPrice)
  } catch {
    ElMessage.warning('行情暂不可用，仍可保存登记价值')
  } finally {
    quoting.value = false
  }
}

async function onEstimate() {
  const f = assetForm.value
  if (f.assetType === '车辆' && !f.carModel) {
    ElMessage.warning('请先选择车型')
    return
  }
  if (f.assetType === '房产' && (!f.city || !f.community)) {
    ElMessage.warning('请先填写城市和小区')
    return
  }
  quoting.value = true
  try {
    const data = f.assetType === '车辆'
      ? await usedCarPrice({
          carModel: f.carModel,
          city: f.city,
          modelYear: f.modelYear,
          mileageKm: f.mileageKm,
          cost: f.cost,
          buyDate: f.buyDate
        })
      : await estimateAsset({
          type: f.assetType,
          carModel: f.carModel,
          city: f.city,
          community: f.community,
          areaSqm: f.areaSqm,
          modelYear: f.modelYear,
          mileageKm: f.mileageKm,
          cost: f.cost,
          buyDate: f.buyDate
        })
    if (!data?.ok) {
      ElMessage.warning(data?.reason || '暂无法估值，请填写取得成本和年份后再试')
      return
    }
    if (data.estimate != null) {
      assetForm.value.amount = Number(data.estimate)
    }
    ElMessage.success(data.note || '已填入估值（仅供参考）')
  } catch {
    ElMessage.warning('暂无法估值，仍可保存登记价值')
  } finally {
    quoting.value = false
  }
}

async function saveAsset() {
  await assetRef.value.validate()
  saving.value = true
  try {
    const payload = { ...assetForm.value }
    delete payload.carPath
    const t = payload.assetType
    if (t !== '股票' && t !== '基金') {
      payload.symbol = null
      payload.shares = null
    }
    if (t !== '存款') {
      payload.annualRate = null
      payload.termMonths = null
      payload.interestMethod = null
    } else if (!payload.interestMethod) {
      payload.interestMethod = 'simple'
    }
    if (t !== '车辆') {
      payload.carModel = null
      payload.mileageKm = null
      payload.modelYear = null
    }
    if (t !== '房产') {
      payload.community = null
      payload.areaSqm = null
    }
    if (t !== '车辆' && t !== '房产') {
      payload.city = null
    }
    if (payload.id) {
      await updateAsset(payload.id, payload)
      ElMessage.success('修改成功')
    } else {
      await addAsset(payload)
      ElMessage.success('新增成功')
    }
    assetDialog.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function onDeleteAsset(row) {
  await ElMessageBox.confirm(`确认删除资产【${row.assetName}】？`, '删除确认', { type: 'warning' })
  await deleteAsset(row.id)
  ElMessage.success('删除成功')
  await load()
}

function openLoan(row) {
  loanForm.value = row
    ? {
        id: row.id,
        loanName: row.loanName,
        loanType: row.loanType,
        totalAmount: Number(row.totalAmount),
        monthlyPayment: Number(row.monthlyPayment),
        totalMonths: row.totalMonths,
        paidMonths: row.paidMonths,
        startDate: row.startDate
      }
    : emptyLoan()
  loanDialog.value = true
}

async function saveLoan() {
  await loanRef.value.validate()
  saving.value = true
  try {
    if (loanForm.value.id) {
      await updateLoan(loanForm.value.id, loanForm.value)
      ElMessage.success('修改成功')
    } else {
      await addLoan(loanForm.value)
      ElMessage.success('新增成功')
    }
    loanDialog.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function onDeleteLoan(row) {
  await ElMessageBox.confirm(`确认删除贷款【${row.loanName}】？`, '删除确认', { type: 'warning' })
  await deleteLoan(row.id)
  ElMessage.success('删除成功')
  await load()
}
</script>

<style scoped>
.cards .el-col {
  margin-bottom: 14px;
}

.bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.el-row + .el-row .page-card,
.el-row + .page-card {
  margin-top: 0;
}

.page-card {
  margin-bottom: 14px;
}
</style>
