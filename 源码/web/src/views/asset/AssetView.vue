<template>
  <div v-loading="loading">
    <!-- 净资产总览 -->
    <el-row :gutter="14" class="cards">
      <el-col :md="6" :sm="12">
        <StatCard label="资产合计" :value="summary.totalAsset" color="#2e7d5b"
                  :sub="`共 ${summary.assetCount || 0} 项资产`" />
      </el-col>
      <el-col :md="6" :sm="12">
        <StatCard label="贷款剩余本金" :value="summary.totalLoanRemain" color="#d9534f"
                  :sub="`共 ${summary.loanCount || 0} 笔贷款`" />
      </el-col>
      <el-col :md="6" :sm="12">
        <StatCard label="净资产" :value="summary.netAsset"
                  :color="Number(summary.netAsset) >= 0 ? '#21a675' : '#d9534f'"
                  sub="净资产 = 资产合计 − 贷款剩余本金" />
      </el-col>
      <el-col :md="6" :sm="12">
        <StatCard label="每月还款压力" :value="summary.monthlyPayTotal" color="#e6a23c"
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
            <el-table-column label="类型" width="72" align="center">
              <template #default="{ row }">
                <el-tag size="small" effect="plain">{{ row.assetType }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="当前价值" width="116" align="right">
              <template #default="{ row }">¥{{ money(row.amount) }}</template>
            </el-table-column>
            <el-table-column label="取得成本" width="116" align="right">
              <template #default="{ row }">
                <span v-if="row.cost">¥{{ money(row.cost) }}</span>
                <span v-else class="text-light">—</span>
              </template>
            </el-table-column>
            <el-table-column label="盈亏" width="116" align="right">
              <template #default="{ row }">
                <span v-if="row.cost" :class="row.amount - row.cost >= 0 ? 'amount-income' : 'amount-expense'">
                  {{ row.amount - row.cost >= 0 ? '+' : '-' }}{{ money(Math.abs(row.amount - row.cost)) }}
                </span>
                <span v-else class="text-light">—</span>
              </template>
            </el-table-column>
            <el-table-column prop="buyDate" label="取得日期" width="96" />
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
    <el-dialog v-model="assetDialog" :title="assetForm.id ? '编辑资产' : '新增资产'" width="460px" destroy-on-close>
      <el-form ref="assetRef" :model="assetForm" :rules="assetRules" label-width="88px">
        <el-form-item label="资产名称" prop="assetName">
          <el-input v-model="assetForm.assetName" placeholder="如：城东住房" maxlength="100" />
        </el-form-item>
        <el-form-item label="资产类型" prop="assetType">
          <el-select v-model="assetForm.assetType" placeholder="请选择" style="width: 100%">
            <el-option v-for="t in assetTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="当前价值" prop="amount">
          <el-input-number v-model="assetForm.amount" :min="0" :precision="2" :step="10000"
                           controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="取得成本">
          <el-input-number v-model="assetForm.cost" :min="0" :precision="2" :step="10000"
                           controls-position="right" style="width: 100%" />
          <div class="text-light">填写后可自动计算盈亏</div>
        </el-form-item>
        <el-form-item label="取得日期">
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
        <el-form-item label="已还期数" prop="paidMonths">
          <el-input-number v-model="loanForm.paidMonths" :min="0" :max="loanForm.totalMonths || 600"
                           controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="起始还款日">
          <el-date-picker v-model="loanForm.startDate" type="date" value-format="YYYY-MM-DD"
                          style="width: 100%" />
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
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import EChart from '../../components/EChart.vue'
import StatCard from '../../components/StatCard.vue'
import {
  addAsset, addLoan, assetSummary, deleteAsset, deleteLoan,
  listAsset, listLoan, updateAsset, updateLoan
} from '../../api/asset'
import { CHART_COLORS, money } from '../../utils/format'

const assetTypes = ['房产', '车辆', '存款', '股票', '基金', '其他']
const loanTypes = ['房贷', '车贷', '消费贷']

const loading = ref(false)
const saving = ref(false)
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

function emptyAsset() {
  return { id: null, assetName: '', assetType: '', amount: 0, cost: null, buyDate: null, remark: '' }
}

function emptyLoan() {
  return {
    id: null, loanName: '', loanType: '', totalAmount: 0,
    monthlyPayment: 0, totalMonths: 240, paidMonths: 0, startDate: null
  }
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    const [s, a, l] = await Promise.all([assetSummary(), listAsset(), listLoan()])
    summary.value = s
    assets.value = a
    loans.value = l
  } finally {
    loading.value = false
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

function openAsset(row) {
  assetForm.value = row
    ? {
        id: row.id,
        assetName: row.assetName,
        assetType: row.assetType,
        amount: Number(row.amount),
        cost: row.cost === null ? null : Number(row.cost),
        buyDate: row.buyDate,
        remark: row.remark
      }
    : emptyAsset()
  assetDialog.value = true
}

async function saveAsset() {
  await assetRef.value.validate()
  saving.value = true
  try {
    if (assetForm.value.id) {
      await updateAsset(assetForm.value.id, assetForm.value)
      ElMessage.success('修改成功')
    } else {
      await addAsset(assetForm.value)
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
