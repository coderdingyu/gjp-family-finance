<template>
  <div v-loading="loading">
    <div class="page-card toolbar">
      <div>
        <span class="card-title inline">智能分析</span>
        <span class="text-light">
          基于客观数据给出结论、依据与建议 —— 回答"钱为什么多花了"，而不只是"花了多少"
        </span>
        <el-tag v-if="agentConfigured" type="success" size="small" effect="plain" style="margin-left: 8px">
          智能体已接入：规则判定阈值，工作流润色并给出总判断
        </el-tag>
        <el-tag v-else type="info" size="small" effect="plain" style="margin-left: 8px">
          当前为本机规则文案
        </el-tag>
      </div>
      <div class="tools">
        <MemberScope v-model="memberId" @change="load" />
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
        <el-button size="small" type="primary" :icon="Refresh" @click="load">重新分析</el-button>
      </div>
    </div>

    <div class="page-card">
      <div class="legend">
        <el-tag type="danger" effect="dark" size="small">需立即关注 {{ countOf('danger') }}</el-tag>
        <el-tag type="warning" effect="dark" size="small">需留意 {{ countOf('warning') }}</el-tag>
        <el-tag type="info" effect="dark" size="small">提示 {{ countOf('info') }}</el-tag>
        <el-tag type="success" effect="dark" size="small">表现良好 {{ countOf('good') }}</el-tag>
      </div>

      <el-empty v-if="!items.length" description="所选区间内没有可分析的数据" />

      <div v-for="item in items" :key="item.code + item.title" class="item" :class="[item.level, { verdict: isS0(item) }]">
        <div class="item-head">
          <el-icon :size="17"><component :is="iconOf(item.level)" /></el-icon>
          <span class="title">{{ item.title }}</span>
          <el-tag v-if="isS0(item)" size="small" type="danger" effect="dark">总判断</el-tag>
          <el-tag size="small" effect="plain" class="code">{{ item.code }}</el-tag>
        </div>
        <div class="row">
          <span class="tag">数据依据</span>
          <span class="content">{{ item.basis }}</span>
        </div>
        <div class="row">
          <span class="tag suggest">处理建议</span>
          <span class="content">{{ item.suggestion }}</span>
        </div>
      </div>
    </div>

    <div class="page-card">
      <h3 class="card-title">分析规则说明</h3>
      <el-table :data="rules" border stripe size="small">
        <el-table-column prop="code" label="编号" width="66" align="center" />
        <el-table-column prop="name" label="规则" width="180" />
        <el-table-column prop="logic" label="判定逻辑" min-width="320" />
      </el-table>
      <p class="text-light" style="margin-top: 10px; line-height: 1.8">
        说明：以上阈值定义在后端 AnalysisService 的常量中，可根据家庭实际情况调整；
        每条结论都同时给出参与计算的原始数字，便于核对，也便于测试用例逐条验证。
        配置 DIFY_ANALYSIS_API_KEY 后，命中规则仍由 Java 判定；智能体润色 A* 文案，并追加 S0 总判断（以及可选的 S1/S2）。
        失败时自动回退本机 A* 文案。
      </p>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { CircleCheck, InfoFilled, Refresh, WarningFilled } from '@element-plus/icons-vue'
import MemberScope from '../../components/MemberScope.vue'
import { analysisConfig, analysisReport } from '../../api/analysis'
import { toDateStr } from '../../utils/format'

const loading = ref(false)
const items = ref([])
const range = ref(defaultRange())
const memberId = ref(null)
const agentConfigured = ref(false)

function defaultRange() {
  const y = new Date().getFullYear()
  return [`${y}-01-01`, toDateStr(new Date())]
}

const rules = [
  { code: 'A1', name: '异常月份归因', logic: '找出支出最高的月份，与其余月份平均支出对比；超出 30% 即判定异常，并钻取到一级分类找出超支贡献最大的分类，再根据该分类在其他月份的水平判断是偶发还是会持续' },
  { code: 'A2', name: '环比分析', logic: '取最近两个完整有数据月份，未过完的当月不参与；支出涨跌幅超过 ±20% 时给出提示' },
  { code: 'A3', name: '结余健康度', logic: '结余率 =（收入 − 支出）/ 收入；为负判定入不敷出，低于 10% 判定储蓄空间不足' },
  { code: 'A4', name: '成员预算预警', logic: '扫描区间内每个完整月的预算执行，使用率 > 100% 判定超支，≥ 80% 判定接近上限；同一成员多月超支合并为一条' },
  { code: 'A5', name: '支出结构集中度', logic: '占比最高的一级分类超过总支出 50% 时提示结构单一，并列出前三分类' },
  { code: 'A6', name: '商家消费集中度', logic: '单一商家占已填写商家消费的 20% 以上时提示消费集中；房租房贷月供不计入商家榜' },
  { code: 'A7', name: '片区消费分布', logic: '片区是课纲要求的可选维度，填写不规范时结论参考即可。单一片区占比达 40% 以上时可提示区域集中' },
  { code: 'A8', name: '人情往来专项', logic: '标记为人情往来的支出占总支出 10% 以上时提示占比偏高' }
]

onMounted(async () => {
  try {
    const cfg = await analysisConfig()
    agentConfigured.value = !!cfg?.configured
  } catch (e) {
    agentConfigured.value = false
  }
  await load()
})

async function load() {
  if (!range.value || range.value.length !== 2) return
  loading.value = true
  try {
    const params = { startDate: range.value[0], endDate: range.value[1] }
    if (memberId.value) {
      params.memberId = memberId.value
    }
    items.value = await analysisReport(params)
  } finally {
    loading.value = false
  }
}

function countOf(level) {
  return items.value.filter((i) => i.level === level).length
}

function iconOf(level) {
  if (level === 'danger' || level === 'warning') return WarningFilled
  if (level === 'good') return CircleCheck
  return InfoFilled
}

function isS0(item) {
  return item?.code === 'S0'
}
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
  gap: 8px;
}

.legend {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

.item {
  border-left: 4px solid;
  border-radius: 4px;
  padding: 13px 16px;
  margin-bottom: 12px;
  background: #fafafa;
}

.item.danger {
  border-color: #d9534f;
  background: #fef4f4;
}

.item.warning {
  border-color: #e6a23c;
  background: #fdf6ec;
}

.item.info {
  border-color: #409eff;
  background: #f4f8fe;
}

.item.good {
  border-color: #21a675;
  background: #f2faf6;
}

.item-head {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 9px;
}

.item.danger .item-head {
  color: #d9534f;
}

.item.warning .item-head {
  color: #c98a20;
}

.item.info .item-head {
  color: #2f7fd1;
}

.item.good .item-head {
  color: #1c8f63;
}

.item.verdict {
  border-left-width: 6px;
  box-shadow: 0 2px 10px rgba(46, 125, 91, 0.14);
}

.item.verdict .title {
  font-size: 16px;
}

.item-head .title {
  font-size: 15px;
  font-weight: 600;
}

.item-head .code {
  margin-left: auto;
}

.row {
  display: flex;
  gap: 9px;
  margin-top: 5px;
  line-height: 1.85;
}

.tag {
  flex: none;
  font-size: 12px;
  color: #fff;
  background: #909399;
  border-radius: 3px;
  padding: 1px 6px;
  height: 20px;
  line-height: 18px;
  margin-top: 3px;
}

.tag.suggest {
  background: var(--gjp-primary);
}

.content {
  color: var(--gjp-text);
  font-size: 13px;
}
</style>
