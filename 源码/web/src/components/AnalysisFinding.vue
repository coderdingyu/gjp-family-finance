<template>
  <article :id="'finding-' + item.findingId" class="finding" :class="item.level">
    <div class="heading">
      <el-tag size="small" :type="tagType">{{ label }}</el-tag>
      <h3>{{ item.title }}</h3>
    </div>
    <p>{{ item.suggestion }}</p>
    <details>
      <summary>展开数据依据 <span class="text-light">· 本机计算，AI 不可改写</span></summary>
      <p class="basis">{{ item.basis }}</p>
      <dl v-if="Object.keys(item.metrics || {}).length">
        <template v-for="(value, key) in item.metrics" :key="key">
          <dt>{{ metricLabels[key] || key }}</dt><dd>{{ Array.isArray(value) ? value.join('、') : value }}</dd>
        </template>
      </dl>
      <small class="text-light">证据编号：{{ item.findingId }}</small>
    </details>
    <div class="actions">
      <el-button size="small" @click="$emit('evidence', item)">{{ evidenceLabel }}</el-button>
      <el-button v-if="item.code === 'A4' && canManage" size="small" text @click="$emit('budget')">查看成员预算</el-button>
    </div>
  </article>
</template>

<script setup>
import { computed } from 'vue'
const props = defineProps({ item: { type: Object, required: true }, canManage: Boolean })
defineEmits(['evidence', 'budget'])
const tagType = computed(() => ({ danger: 'danger', warning: 'warning', good: 'success', info: 'info' }[props.item.level] || 'info'))
const label = computed(() => ({ danger: '优先核对', warning: '需留意', good: '账面良好', info: '参考信息' }[props.item.level] || '参考信息'))
const evidenceLabel = computed(() => props.item.code === 'A6' ? '查看区间流水（含其他商家）' : '查看对应流水')
const metricLabels = { amount: '本期金额（元）', baseline: '可比月均（元）', delta: '差额（元）', sampleMonths: '同口径样本月份',
  topCategoryDelta: '最大分类差额（元）', previousMonth: '上月', currentMonth: '本月', previousAmount: '上月金额（元）',
  rate: '变化 / 结余率（%）', frequencyEffect: '次数变化分解（元）', averageEffect: '单笔均额变化分解（元）',
  previousCount: '上月笔数', currentCount: '本月笔数', topCategoryId: '归因分类 ID', income: '收入（元）', expense: '支出（元）',
  balance: '结余（元）', memberId: '成员 ID', month: '月份', budget: '当前月预算（元）', usedRate: '预算使用率（%）',
  ratio: '金额占比（%）', count: '笔数', merchantCoverage: '商家填写覆盖率（按支出笔数，%）' }
</script>

<style scoped>
.finding { padding: 18px; margin: 12px 0; border: 1px solid #e6ebe8; border-left: 4px solid #8fa59a; border-radius: 8px; background: #fff; scroll-margin-top: 20px; }
.finding.danger { border-left-color: #cf5454; }.finding.warning { border-left-color: #d49b3d; }.finding.good { border-left-color: #318163; }
.heading { display: flex; align-items: flex-start; gap: 10px; }.heading h3 { margin: 0; font-size: 15px; line-height: 1.6; }
p { font-size: 14px; line-height: 1.8; }.basis { white-space: pre-wrap; } summary { cursor: pointer; font-size: 13px; }
.actions { margin-top: 14px; display: flex; flex-wrap: wrap; gap: 8px; } dl { display: grid; grid-template-columns: minmax(140px, auto) 1fr; font-size: 13px; gap: 6px 16px; } dt { color: #68766f; } dd { margin: 0; overflow-wrap: anywhere; }
@media (max-width: 600px) { .heading { flex-wrap: wrap; } dl { grid-template-columns: 1fr; } }
</style>
