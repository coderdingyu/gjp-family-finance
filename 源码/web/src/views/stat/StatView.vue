<template>
  <div v-loading="loading">
    <div class="page-card toolbar">
      <div>
        <span class="card-title inline">统计报表</span>
        <span class="text-light">只呈现客观汇总数据；问题诊断请看"智能分析"</span>
      </div>
      <div class="tools">
        <MemberScope v-model="memberId" @change="load" />
        <el-radio-group v-model="type" size="small" @change="load">
          <el-radio-button :value="2">支出</el-radio-button>
          <el-radio-button :value="1">收入</el-radio-button>
        </el-radio-group>
        <el-date-picker
          v-model="range"
          type="daterange"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          size="small"
          style="width: 240px"
          @change="load"
        />
        <el-button size="small" @click="quick('year')">本年</el-button>
        <el-button size="small" @click="quick('month')">本月</el-button>
        <el-button size="small" @click="quick('lastYear')">去年</el-button>
      </div>
    </div>

    <el-row :gutter="14">
      <el-col :md="12">
        <div class="page-card">
          <h3 class="card-title">{{ typeName }}趋势（按月）</h3>
          <EChart :option="trendOption" :empty="!trendRows.length" height="300px" />
        </div>
      </el-col>
      <el-col :md="12">
        <div class="page-card">
          <h3 class="card-title">
            {{ typeName }}分类占比
            <span class="text-light">（点击扇区可钻取二级分类）</span>
          </h3>
          <EChart ref="pieRef" :option="catPieOption" :empty="!cats.length" height="300px" />
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="14">
      <el-col :md="12">
        <div class="page-card">
          <h3 class="card-title">
            分类明细
            <el-button v-if="drillParent" link type="primary" size="small" @click="clearDrill">
              返回一级分类
            </el-button>
          </h3>
          <el-table :data="drillParent ? subCats : cats" border stripe size="small" max-height="300">
            <el-table-column type="index" label="#" width="46" align="center" />
            <el-table-column prop="name" label="分类" min-width="120">
              <template #default="{ row }">
                <el-button v-if="!drillParent && row.id" link type="primary" @click="drill(row)">
                  {{ row.name }}
                </el-button>
                <span v-else>{{ row.name }}</span>
              </template>
            </el-table-column>
            <el-table-column label="金额" width="120" align="right">
              <template #default="{ row }">¥{{ money(row.amount) }}</template>
            </el-table-column>
            <el-table-column prop="count" label="笔数" width="70" align="center" />
            <el-table-column label="占比" width="160">
              <template #default="{ row }">
                <div class="ratio-cell">
                  <el-progress
                    :percentage="Number(row.ratio)"
                    :stroke-width="8"
                    :show-text="false"
                    color="var(--gjp-primary)"
                  />
                  <span class="ratio-num">{{ row.ratio }}%</span>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>
      <el-col :md="12">
        <div class="page-card">
          <h3 class="card-title">
            {{ memberId ? `所选成员${typeName}合计` : `成员${typeName}对比` }}
          </h3>
          <EChart :option="memberOption" :empty="!memberRows.length" height="300px" />
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="14">
      <el-col :md="8">
        <div class="page-card">
          <h3 class="card-title">支付方式构成</h3>
          <EChart :option="payOption" :empty="!payRows.length" height="280px" />
        </div>
      </el-col>
      <el-col :md="8">
        <div class="page-card">
          <h3 class="card-title">商家消费排行</h3>
          <el-table :data="merchants" border stripe size="small" max-height="280"
                    empty-text="流水中还没有填写商家信息">
            <el-table-column type="index" label="#" width="46" align="center" />
            <el-table-column prop="name" label="商家" min-width="90" show-overflow-tooltip />
            <el-table-column label="金额" width="100" align="right">
              <template #default="{ row }">¥{{ money(row.amount) }}</template>
            </el-table-column>
            <el-table-column prop="count" label="笔数" width="66" align="center" />
          </el-table>
        </div>
      </el-col>
      <el-col :md="8">
        <div class="page-card">
          <h3 class="card-title">消费片区分布</h3>
          <el-table :data="areas" border stripe size="small" max-height="280"
                    empty-text="流水中还没有填写消费片区">
            <el-table-column type="index" label="#" width="46" align="center" />
            <el-table-column prop="name" label="片区" min-width="80" />
            <el-table-column label="金额" width="100" align="right">
              <template #default="{ row }">¥{{ money(row.amount) }}</template>
            </el-table-column>
            <el-table-column label="占比" width="78" align="right">
              <template #default="{ row }">{{ row.ratio }}%</template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import EChart from '../../components/EChart.vue'
import MemberScope from '../../components/MemberScope.vue'
import {
  areaStat,
  categoryStat,
  memberStat,
  merchantRank,
  payMethodStat,
  subCategoryStat,
  trend
} from '../../api/stat'
import { CHART_COLORS, TONE, money, toDateStr } from '../../utils/format'

const loading = ref(false)
const type = ref(2)
const range = ref(defaultRange())
const memberId = ref(null)

const trendRows = ref([])
const cats = ref([])
const subCats = ref([])
const drillParent = ref(null)
const memberRows = ref([])
const payRows = ref([])
const merchants = ref([])
const areas = ref([])

const typeName = computed(() => (type.value === 1 ? '收入' : '支出'))

function defaultRange() {
  const y = new Date().getFullYear()
  return [`${y}-01-01`, toDateStr(new Date())]
}

function quick(kind) {
  const now = new Date()
  const y = now.getFullYear()
  if (kind === 'year') {
    range.value = [`${y}-01-01`, `${y}-12-31`]
  } else if (kind === 'month') {
    const m = String(now.getMonth() + 1).padStart(2, '0')
    const last = new Date(y, now.getMonth() + 1, 0).getDate()
    range.value = [`${y}-${m}-01`, `${y}-${m}-${last}`]
  } else {
    range.value = [`${y - 1}-01-01`, `${y - 1}-12-31`]
  }
  load()
}

const params = computed(() => {
  const p = { startDate: range.value?.[0], endDate: range.value?.[1] }
  if (memberId.value) {
    p.memberId = memberId.value
  }
  return p
})

onMounted(load)

async function load() {
  if (!range.value || range.value.length !== 2) return
  loading.value = true
  clearDrill()
  try {
    // 各图表互不依赖，并行请求比串行快很多
    const [t, c, m, p, mer, a] = await Promise.all([
      trend(params.value),
      categoryStat({ ...params.value, type: type.value }),
      memberStat({ ...params.value, type: type.value }),
      payMethodStat({ ...params.value, type: type.value }),
      merchantRank({ ...params.value, limit: 15 }),
      areaStat(params.value)
    ])
    trendRows.value = t
    cats.value = c
    memberRows.value = m
    payRows.value = p
    merchants.value = mer
    areas.value = a
  } finally {
    loading.value = false
  }
}

async function drill(row) {
  const list = await subCategoryStat({ ...params.value, parentId: row.id })
  if (!list.length) {
    subCats.value = []
    drillParent.value = row
    return
  }
  subCats.value = list
  drillParent.value = row
}

function clearDrill() {
  drillParent.value = null
  subCats.value = []
}

const trendOption = computed(() => {
  const key = type.value === 1 ? 'income' : 'expense'
  return {
    color: [type.value === 1 ? TONE.income : TONE.expense],
    tooltip: { trigger: 'axis', valueFormatter: (v) => `¥${money(v)}` },
    grid: { left: 62, right: 24, top: 24, bottom: 30 },
    xAxis: { type: 'category', data: trendRows.value.map((r) => r.ym), boundaryGap: false },
    yAxis: { type: 'value', axisLabel: { formatter: (v) => (v >= 10000 ? v / 10000 + '万' : v) } },
    series: [
      {
        name: typeName.value,
        type: 'line',
        smooth: true,
        areaStyle: { opacity: 0.15 },
        symbolSize: 6,
        data: trendRows.value.map((r) => Number(r[key]))
      }
    ]
  }
})

const catPieOption = computed(() => {
  const rows = drillParent.value ? subCats.value : cats.value
  const title = drillParent.value ? `${drillParent.value.name} 明细` : `${typeName.value}分类`
  const ratioOf = {}
  rows.forEach((r) => {
    ratioOf[r.name] = Number(r.ratio).toFixed(1)
  })
  return {
    color: CHART_COLORS,
    title: { text: title, left: 'center', bottom: 0, textStyle: { fontSize: 12, color: '#909399' } },
    tooltip: { trigger: 'item', formatter: (p) => `${p.name}<br/>¥${money(p.value)}（${p.percent}%）` },
    legend: {
      type: 'scroll',
      orient: 'vertical',
      right: 4,
      top: 14,
      bottom: 30,
      itemWidth: 10,
      itemHeight: 10,
      textStyle: { fontSize: 12 },
      // 占比写进图例，不画外置标签：分类多时外置标签一定会互相压字
      formatter: (n) => `${n}  ${ratioOf[n] ?? 0}%`
    },
    series: [
      {
        type: 'pie',
        radius: ['42%', '68%'],
        center: ['28%', '46%'],
        itemStyle: { borderColor: '#fff', borderWidth: 2 },
        label: { show: false },
        labelLine: { show: false },
        emphasis: { label: { show: true, fontSize: 14, fontWeight: 600, formatter: '{d}%' } },
        data: rows.map((r) => ({ name: r.name, value: Number(r.amount) }))
      }
    ]
  }
})

const memberOption = computed(() => ({
  color: [type.value === 1 ? TONE.income : TONE.primary],
  tooltip: {
    trigger: 'axis',
    axisPointer: { type: 'shadow' },
    formatter: (ps) => {
      const row = memberRows.value[ps[0].dataIndex]
      return `${row.name}<br/>金额 ¥${money(row.amount)}<br/>笔数 ${row.count} 笔<br/>占比 ${row.ratio}%`
    }
  },
  grid: { left: 62, right: 30, top: 24, bottom: 30 },
  xAxis: { type: 'category', data: memberRows.value.map((r) => r.name) },
  yAxis: { type: 'value', axisLabel: { formatter: (v) => (v >= 10000 ? v / 10000 + '万' : v) } },
  series: [
    {
      type: 'bar',
      barMaxWidth: 46,
      label: { show: true, position: 'top', formatter: (p) => money(p.value) },
      data: memberRows.value.map((r) => Number(r.amount))
    }
  ]
}))

const payOption = computed(() => ({
  color: CHART_COLORS,
  tooltip: { trigger: 'item', formatter: (p) => `${p.name}<br/>¥${money(p.value)}（${p.percent}%）` },
  legend: { bottom: 0, itemWidth: 10, itemHeight: 10 },
  series: [
    {
      type: 'pie',
      radius: '62%',
      center: ['50%', '45%'],
      itemStyle: { borderColor: '#fff', borderWidth: 2 },
      label: { formatter: '{b}\n{d}%', fontSize: 11 },
      labelLine: { length: 8, length2: 8 },
      data: payRows.value.map((r) => ({ name: r.name, value: Number(r.amount) }))
    }
  ]
}))
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
  padding: 14px 20px;
}

.card-title.inline {
  display: inline-block;
  margin: 0 12px 0 0;
}

.tools {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.el-row + .el-row .page-card {
  margin-top: 14px;
}

.el-row:first-of-type .page-card {
  margin-top: 14px;
}

.ratio-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ratio-cell :deep(.el-progress) {
  flex: 1;
}

.ratio-num {
  flex: none;
  width: 52px;
  text-align: right;
  font-size: 12px;
  color: var(--gjp-text-light);
}
</style>
