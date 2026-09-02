<template>
  <div v-loading="loading">
    <!-- 区间选择 -->
    <div class="page-card toolbar">
      <div>
        <span class="card-title inline">{{ boardTitle }}</span>
        <span class="text-light">统计区间：{{ data.range || '—' }}</span>
      </div>
      <div class="tools">
        <MemberScope v-model="memberId" @change="load" />
        <el-radio-group v-model="mode" @change="onModeChange" size="small">
          <el-radio-button value="year">按年</el-radio-button>
          <el-radio-button value="month">按月</el-radio-button>
        </el-radio-group>
        <el-date-picker
          v-if="mode === 'year'"
          v-model="year"
          type="year"
          value-format="YYYY"
          placeholder="选择年份"
          size="small"
          style="width: 120px; margin-left: 10px"
          @change="load"
        />
        <el-date-picker
          v-else
          v-model="month"
          type="month"
          value-format="YYYY-MM"
          placeholder="选择月份"
          size="small"
          style="width: 140px; margin-left: 10px"
          @change="load"
        />
      </div>
    </div>

    <!-- 指标卡 -->
    <el-row :gutter="14" class="cards">
      <el-col :md="6" :sm="12">
        <StatCard label="收入合计" :value="ov.totalIncome" color="#21a675"
                  :sub="`月均 ¥${money(ov.avgMonthlyIncome)}`" />
      </el-col>
      <el-col :md="6" :sm="12">
        <StatCard label="支出合计" :value="ov.totalExpense" color="#d9534f"
                  :sub="`月均 ¥${money(ov.avgMonthlyExpense)}`" />
      </el-col>
      <el-col :md="6" :sm="12">
        <StatCard :label="data.memberId ? '本人结余' : '家庭结余'" :value="ov.balance"
                  :color="Number(ov.balance) >= 0 ? '#2e7d5b' : '#d9534f'"
                  :sub="`结余率 ${balanceRate}%`" />
      </el-col>
      <el-col :md="6" :sm="12">
        <StatCard label="流水笔数" :value="ov.recordCount" color="#409eff" prefix="" raw
                  :sub="`单笔最大支出 ¥${money(ov.maxExpense)}`" />
      </el-col>
    </el-row>

    <!-- 趋势 + 支出结构 -->
    <el-row :gutter="14">
      <el-col :md="16">
        <div class="page-card">
          <h3 class="card-title">收支趋势</h3>
          <EChart :option="trendOption" :empty="!data.trend?.length" height="330px" />
        </div>
      </el-col>
      <el-col :md="8">
        <div class="page-card">
          <h3 class="card-title">支出结构（一级分类）</h3>
          <EChart :option="expensePieOption" :empty="!data.expenseCategory?.length" height="330px" />
        </div>
      </el-col>
    </el-row>

    <!-- 成员 + 商家 -->
    <el-row :gutter="14">
      <el-col :md="12">
        <div class="page-card">
          <h3 class="card-title">
            {{ data.memberId ? '本人支出合计' : '成员支出对比' }}
          </h3>
          <EChart :option="memberBarOption" :empty="!data.memberExpense?.length" height="300px" />
        </div>
      </el-col>
      <el-col :md="12">
        <div class="page-card">
          <h3 class="card-title">商家消费排行 TOP10</h3>
          <EChart :option="merchantBarOption" :empty="!data.merchantRank?.length" height="300px"
                  empty-text="流水中还没有填写商家信息" />
        </div>
      </el-col>
    </el-row>

    <!-- 片区 + 支付方式 + 预算 -->
    <el-row :gutter="14">
      <el-col :md="8">
        <div class="page-card">
          <h3 class="card-title">消费片区分布</h3>
          <EChart :option="areaPieOption" :empty="!data.areaStat?.length" height="290px"
                  empty-text="流水中还没有填写消费片区" />
        </div>
      </el-col>
      <el-col :md="8">
        <div class="page-card">
          <h3 class="card-title">支付方式构成</h3>
          <EChart :option="payPieOption" :empty="!data.payMethod?.length" height="290px" />
        </div>
      </el-col>
      <el-col :md="8">
        <div class="page-card budget-card">
          <h3 class="card-title">成员预算执行（{{ data.budgetMonth || '当月' }}）</h3>
          <div v-if="!data.budget?.length" class="text-light">暂无成员数据</div>
          <div v-for="b in data.budget" :key="b.memberId" class="budget-row">
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
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import EChart from '../../components/EChart.vue'
import MemberScope from '../../components/MemberScope.vue'
import StatCard from '../../components/StatCard.vue'
import { dashboard } from '../../api/stat'
import { CHART_COLORS, currentMonth, currentYear, money } from '../../utils/format'

const loading = ref(false)
const data = ref({})
const mode = ref('year')
const year = ref(String(currentYear()))
const month = ref(currentMonth())
/** 户主选择要查看的成员；不选表示全家汇总。普通成员由后端强制成自己 */
const memberId = ref(null)

const ov = computed(() => data.value.overview || {})

/** 标题随数据范围变化，避免只看一个人的数据却写着"家庭收支" */
const boardTitle = computed(() => {
  if (data.value.memberName) return `${data.value.memberName} 的收支看板`
  return '家庭收支看板'
})

const balanceRate = computed(() => {
  const income = Number(ov.value.totalIncome || 0)
  if (income === 0) return '0.00'
  return ((Number(ov.value.balance || 0) / income) * 100).toFixed(2)
})

onMounted(load)

function onModeChange() {
  load()
}

async function load() {
  loading.value = true
  try {
    const params =
      mode.value === 'year'
        ? { year: Number(year.value) }
        : { year: Number(month.value.slice(0, 4)), month: Number(month.value.slice(5, 7)) }
    if (memberId.value) {
      params.memberId = memberId.value
    }
    data.value = await dashboard(params)
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

/** 折线 + 柱状混合：柱子是收入支出，折线是结余，一张图看清家庭收益 */
const trendOption = computed(() => {
  const rows = data.value.trend || []
  return {
    color: ['#21a675', '#d9534f', '#e6a23c'],
    tooltip: { trigger: 'axis', valueFormatter: (v) => `¥${money(v)}` },
    legend: { data: ['收入', '支出', '结余'], top: 0 },
    grid: { left: 60, right: 30, top: 40, bottom: 30 },
    xAxis: { type: 'category', data: rows.map((r) => r.ym), axisTick: { alignWithLabel: true } },
    yAxis: { type: 'value', axisLabel: { formatter: (v) => (v >= 10000 ? v / 10000 + '万' : v) } },
    series: [
      { name: '收入', type: 'bar', barMaxWidth: 22, data: rows.map((r) => Number(r.income)) },
      { name: '支出', type: 'bar', barMaxWidth: 22, data: rows.map((r) => Number(r.expense)) },
      {
        name: '结余',
        type: 'line',
        smooth: true,
        symbolSize: 6,
        data: rows.map((r) => Number(r.balance))
      }
    ]
  }
})

/**
 * 环形图统一配置。
 * 分类维度动辄十几项，把百分比画成外置标签一定会互相压字、还会串到图例上，
 * 所以这里不画扇区标签，改为把占比直接写进图例文字，鼠标悬浮再看具体金额。
 */
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
const areaPieOption = computed(() => pieOption(data.value.areaStat || [], '片区'))
const payPieOption = computed(() => pieOption(data.value.payMethod || [], '支付方式'))

const memberBarOption = computed(() => {
  const rows = data.value.memberExpense || []
  return {
    color: ['#2e7d5b'],
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, valueFormatter: (v) => `¥${money(v)}` },
    grid: { left: 70, right: 110, top: 20, bottom: 30 },
    xAxis: { type: 'value', axisLabel: { formatter: (v) => (v >= 10000 ? v / 10000 + '万' : v) } },
    yAxis: { type: 'category', data: rows.map((r) => r.name).reverse() },
    series: [
      {
        type: 'bar',
        barMaxWidth: 24,
        label: { show: true, position: 'right', formatter: (p) => `¥${money(p.value)}` },
        data: rows.map((r) => Number(r.amount)).reverse()
      }
    ]
  }
})

const merchantBarOption = computed(() => {
  const rows = data.value.merchantRank || []
  return {
    color: ['#e6a23c'],
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (ps) => {
        const row = rows[rows.length - 1 - ps[0].dataIndex]
        return `${ps[0].name}<br/>金额 ¥${money(row.amount)}<br/>笔数 ${row.count} 笔<br/>占比 ${row.ratio}%`
      }
    },
    grid: { left: 90, right: 60, top: 20, bottom: 30 },
    xAxis: { type: 'value', axisLabel: { formatter: (v) => (v >= 10000 ? v / 10000 + '万' : v) } },
    yAxis: { type: 'category', data: rows.map((r) => r.name).reverse() },
    series: [
      {
        type: 'bar',
        barMaxWidth: 20,
        label: { show: true, position: 'right', formatter: (p) => `¥${money(p.value)}` },
        data: rows.map((r) => Number(r.amount)).reverse()
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

.toolbar .tools {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.card-title.inline {
  display: inline-block;
  margin: 0 12px 0 0;
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
  min-height: 290px;
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
</style>
