<template>
  <div v-loading="loading">
    <div class="page-card toolbar">
      <div>
        <span class="card-title inline">{{ boardTitle }}</span>
        <span class="text-light">只看我自己的账，与家庭看板分开</span>
      </div>
      <div class="text-light week-note">本周 {{ data.weekStart || '—' }} ~ {{ data.weekEnd || '—' }}（周一至周日，合计截到今天）</div>
    </div>

    <el-row :gutter="14" class="cards">
      <el-col :md="6" :sm="12">
        <StatCard label="今日支出" :value="data.todayExpense" color="#d9534f"
                  :sub="data.today || ''" />
      </el-col>
      <el-col :md="6" :sm="12">
        <StatCard label="今日收入" :value="data.todayIncome" color="#21a675"
                  :sub="data.today || ''" />
      </el-col>
      <el-col :md="6" :sm="12">
        <StatCard label="本周支出" :value="data.weekExpense" color="#e6a23c"
                  :sub="'周一至今天'" />
      </el-col>
      <el-col :md="6" :sm="12">
        <StatCard label="本月支出" :value="data.monthExpense" color="#c0392b"
                  :sub="data.month || ''" />
      </el-col>
    </el-row>

    <el-row :gutter="14" class="cards">
      <el-col :md="6" :sm="12">
        <StatCard label="本月收入合计" :value="data.monthIncome" color="#21a675"
                  :sub="data.month || ''" />
      </el-col>
      <el-col :md="6" :sm="12">
        <StatCard label="本月支出合计" :value="data.monthExpense" color="#d9534f"
                  :sub="`单笔最大 ¥${money(ov.maxExpense)}`" />
      </el-col>
      <el-col :md="6" :sm="12">
        <StatCard label="本月结余" :value="data.monthBalance"
                  :color="Number(data.monthBalance) >= 0 ? '#2e7d5b' : '#d9534f'"
                  :sub="monthBalanceSub" />
      </el-col>
      <el-col :md="6" :sm="12">
        <StatCard label="本月笔数" :value="data.monthCount" color="#409eff" prefix="" raw
                  :sub="`人情往来 ¥${money(ov.giftExpense)}`" />
      </el-col>
    </el-row>

    <div class="section-label">上月情况</div>
    <el-row :gutter="14" class="cards">
      <el-col :md="6" :sm="12">
        <StatCard label="上月收入" :value="data.lastMonthIncome" color="#21a675"
                  :sub="data.lastMonthLabel || ''" />
      </el-col>
      <el-col :md="6" :sm="12">
        <StatCard label="上月支出" :value="data.lastMonthExpense" color="#d9534f"
                  :sub="data.lastMonthLabel || ''" />
      </el-col>
      <el-col :md="6" :sm="12">
        <StatCard label="上月结余" :value="data.lastMonthBalance"
                  :color="Number(data.lastMonthBalance) >= 0 ? '#2e7d5b' : '#d9534f'"
                  :sub="data.lastMonthLabel || ''" />
      </el-col>
      <el-col :md="6" :sm="12">
        <StatCard label="上月笔数" :value="data.lastMonthCount" color="#409eff" prefix="" raw
                  :sub="data.lastMonthLabel || ''" />
      </el-col>
    </el-row>

    <el-row :gutter="14">
      <el-col :md="8">
        <div class="page-card">
          <h3 class="card-title">本周按日支出</h3>
          <EChart :option="weekOption" :empty="!hasWeekExpense" height="280px" empty-text="本周还没有支出" />
        </div>
      </el-col>
      <el-col :md="8">
        <div class="page-card">
          <h3 class="card-title">本月支出结构（一级分类）</h3>
          <EChart :option="expensePieOption" :empty="!data.expenseCategory?.length" height="280px" />
        </div>
      </el-col>
      <el-col :md="8">
        <div class="page-card">
          <h3 class="card-title">上月支出结构（一级分类）</h3>
          <EChart :option="lastExpensePieOption" :empty="!data.lastExpenseCategory?.length" height="280px"
                  empty-text="上月没有支出" />
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="14">
      <el-col :md="16">
        <div class="page-card">
          <h3 class="card-title">本月按日支出</h3>
          <EChart :option="monthSparkOption" :empty="!hasMonthExpense" height="240px" empty-text="本月还没有支出" />
        </div>
      </el-col>
      <el-col :md="8">
        <div class="page-card budget-card">
          <h3 class="card-title">本月预算执行（{{ data.budgetMonth || '当月' }}）</h3>
          <div v-if="!budgetRows.length" class="text-light">未设置月度预算</div>
          <div v-for="b in budgetRows" :key="b.memberId" class="budget-row">
            <div class="budget-head">
              <span>{{ b.memberName }}</span>
              <el-tag :type="budgetTag(b.status)" size="small" effect="plain">{{ b.status }}</el-tag>
            </div>
            <el-progress
              :percentage="Math.min(Number(b.usedRate), 100)"
              :status="b.status === '已超支' ? 'exception' : b.status === '接近上限' ? 'warning' : 'success'"
              :stroke-width="10"
            />
            <div class="text-light">
              已支出 ¥{{ money(b.expense) }}
              <template v-if="Number(b.budget) > 0"> / 预算 ¥{{ money(b.budget) }}（{{ b.usedRate }}%）</template>
              <template v-else>（未设置月度预算）</template>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <div class="page-card">
      <h3 class="card-title">最近 5 笔流水</h3>
      <el-table :data="data.recent || []" border stripe size="small" empty-text="还没有流水">
        <el-table-column prop="recordDate" label="日期" width="110" />
        <el-table-column label="类型" width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="row.type === 1 ? 'success' : 'danger'" size="small" effect="light">
              {{ typeText(row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="categoryName" label="分类" min-width="140" />
        <el-table-column label="金额" width="120" align="right">
          <template #default="{ row }">
            <span :class="row.type === 1 ? 'amount-income' : 'amount-expense'">
              {{ row.type === 1 ? '+' : '-' }}{{ money(row.amount) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="merchant" label="商家" min-width="120" show-overflow-tooltip />
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import EChart from '../../components/EChart.vue'
import StatCard from '../../components/StatCard.vue'
import { personalBoard } from '../../api/stat'
import { CHART_COLORS, money, signedMoney, typeText } from '../../utils/format'

const loading = ref(false)
const data = ref({})

const ov = computed(() => data.value.overview || {})
const boardTitle = computed(() => {
  const name = data.value.memberName
  return name ? `${name}的个人看板` : '个人看板'
})
const balanceRate = computed(() => {
  const income = Number(data.value.monthIncome || 0)
  if (income === 0) return '0.00'
  return ((Number(data.value.monthBalance || 0) / income) * 100).toFixed(2)
})
const monthBalanceSub = computed(() => {
  if (data.value.lastMonthLabel == null && data.value.lastMonthBalance == null) {
    return `结余率 ${balanceRate.value}%`
  }
  return `较上月 ${signedMoney(data.value.monthBalanceChange)}`
})
const hasWeekExpense = computed(() => (data.value.weekDaily || []).some((r) => Number(r.expense) > 0))
const hasMonthExpense = computed(() => (data.value.monthDaily || []).some((r) => Number(r.expense) > 0))
const budgetRows = computed(() => (data.value.budget || []).filter((b) => b.status !== '未设预算'))

onMounted(load)

async function load() {
  loading.value = true
  try {
    data.value = await personalBoard()
  } finally {
    loading.value = false
  }
}

function budgetTag(status) {
  if (status === '已超支') return 'danger'
  if (status === '接近上限') return 'warning'
  if (status === '未设预算') return 'info'
  return 'success'
}

function pieOption(rows, name) {
  const ratioOf = {}
  rows.forEach((r) => {
    ratioOf[r.name] = Number(r.ratio).toFixed(1)
  })
  return {
    color: CHART_COLORS,
    tooltip: { trigger: 'item', formatter: (p) => `${p.name}<br/>¥${money(p.value)}（${p.percent}%）` },
    legend: {
      type: 'scroll',
      orient: 'vertical',
      right: 4,
      top: 14,
      bottom: 14,
      itemWidth: 10,
      itemHeight: 10,
      textStyle: { fontSize: 12 },
      formatter: (n) => `${n}  ${ratioOf[n] ?? 0}%`
    },
    series: [
      {
        name,
        type: 'pie',
        radius: ['45%', '70%'],
        center: ['30%', '50%'],
        itemStyle: { borderColor: '#fff', borderWidth: 2 },
        label: { show: false },
        labelLine: { show: false },
        emphasis: { label: { show: true, fontSize: 14, fontWeight: 600, formatter: '{d}%' } },
        data: rows.map((r) => ({ name: r.name, value: Number(r.amount) }))
      }
    ]
  }
}

const expensePieOption = computed(() => pieOption(data.value.expenseCategory || [], '支出'))
const lastExpensePieOption = computed(() => pieOption(data.value.lastExpenseCategory || [], '上月支出'))


const weekOption = computed(() => {
  const rows = data.value.weekDaily || []
  return {
    color: ['#d9534f'],
    tooltip: { trigger: 'axis', valueFormatter: (v) => `¥${money(v)}` },
    grid: { left: 50, right: 20, top: 24, bottom: 30 },
    xAxis: {
      type: 'category',
      data: rows.map((r) => String(r.ym).slice(5)),
      axisTick: { alignWithLabel: true }
    },
    yAxis: { type: 'value', axisLabel: { formatter: (v) => (v >= 10000 ? v / 10000 + '万' : v) } },
    series: [{ name: '支出', type: 'bar', barMaxWidth: 28, data: rows.map((r) => Number(r.expense)) }]
  }
})

const monthSparkOption = computed(() => {
  const rows = data.value.monthDaily || []
  return {
    color: ['#d9534f'],
    tooltip: { trigger: 'axis', valueFormatter: (v) => `¥${money(v)}` },
    grid: { left: 50, right: 16, top: 20, bottom: 28 },
    xAxis: {
      type: 'category',
      data: rows.map((r) => String(r.ym).slice(8)),
      axisLabel: { interval: 4 }
    },
    yAxis: { type: 'value', axisLabel: { formatter: (v) => (v >= 10000 ? v / 10000 + '万' : v) } },
    series: [
      {
        name: '支出',
        type: 'line',
        smooth: true,
        showSymbol: false,
        areaStyle: { opacity: 0.12 },
        data: rows.map((r) => Number(r.expense))
      }
    ]
  }
})
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  flex-wrap: wrap;
  gap: 10px;
}

.card-title.inline {
  display: inline-block;
  margin: 0 12px 0 0;
}

.week-note {
  font-size: 12px;
}

.section-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--gjp-text, #2c3e50);
  margin: 10px 2px 4px;
}

.cards {
  margin: 14px 0 2px;
}

.cards .el-col {
  margin-bottom: 14px;
}

.el-row + .el-row .page-card,
.cards + .el-row .page-card {
  margin-top: 14px;
}

.budget-card {
  min-height: 240px;
}

.budget-row {
  margin-bottom: 14px;
}

.budget-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 5px;
  font-size: 13px;
}

.page-card + .page-card {
  margin-top: 14px;
}
</style>
