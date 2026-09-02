<template>
  <div v-loading="loading">
    <div class="page-card">
      <h3 class="card-title">账单查重</h3>
      <p class="text-light intro">
        查找金额相同、消费时间相同或相近的流水。系统只负责把可疑的挑出来并说明理由，
        <b>删哪一条由你决定</b> —— 同一天在同一家店买两杯一样的咖啡，完全可能是两笔真实消费。
      </p>
      <el-form :inline="true" class="query-bar">
        <el-form-item label="日期容差">
          <el-select v-model="params.dayTolerance" style="width: 150px" @change="scan">
            <el-option label="同一天（严格）" :value="0" />
            <el-option label="相差 1 天内" :value="1" />
            <el-option label="相差 3 天内" :value="3" />
            <el-option label="相差 7 天内" :value="7" />
            <el-option label="相差 30 天内" :value="30" />
          </el-select>
        </el-form-item>
        <el-form-item label="同一成员">
          <el-switch v-model="params.sameMember" @change="scan" />
        </el-form-item>
        <el-form-item label="同一分类">
          <el-switch v-model="params.sameCategory" @change="scan" />
        </el-form-item>
        <el-form-item label="日期范围">
          <el-date-picker
            v-model="range"
            type="daterange"
            start-placeholder="开始"
            end-placeholder="结束"
            value-format="YYYY-MM-DD"
            style="width: 240px"
            @change="scan"
          />
        </el-form-item>
        <el-form-item v-if="!scopeLocked">
          <el-select v-model="params.memberId" placeholder="全部成员" clearable
                     style="width: 140px" @change="scan">
            <el-option v-for="m in members" :key="m.id" :label="m.memberName" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="scan">重新扫描</el-button>
        </el-form-item>
      </el-form>

      <el-alert v-if="result.groupCount === 0" type="success" :closable="false" show-icon
                title="没有发现疑似重复的流水" description="可以放宽日期容差再扫一次。" />
      <div v-else class="summary">
        <span>发现 <b>{{ result.groupCount }}</b> 组疑似重复</span>
        <span>其中多计 <b class="amount-expense">{{ result.extraCount }}</b> 笔</span>
        <span>金额合计 <b class="amount-expense">¥{{ money(result.extraAmount) }}</b></span>
        <span class="text-light">（按每组只保留一条估算）</span>
        <span v-if="selectedIds.length" class="picked">
          已勾选 {{ selectedIds.length }} 笔
          <el-button type="danger" size="small" :icon="Delete" @click="onDelete">删除勾选</el-button>
        </span>
      </div>
      <el-alert v-if="result.truncated" type="warning" :closable="false" show-icon class="trunc"
                title="结果过多，仅显示金额最大的前 200 组" />
    </div>

    <div v-for="(g, gi) in result.groups" :key="gi" class="page-card group">
      <div class="group-head">
        <el-tag :type="g.matchType === '完全一致' ? 'danger' : 'warning'" effect="dark" size="small">
          {{ g.matchType }}
        </el-tag>
        <span class="group-amount">¥{{ money(g.amount) }} × {{ g.count }} 笔</span>
        <span class="text-light group-reason">{{ g.reason }}</span>
        <el-button link type="primary" size="small" @click="pickAllButFirst(g)">
          勾选除最早一条外的全部
        </el-button>
      </div>
      <el-table :data="g.records" border stripe size="small" @selection-change="(v) => onGroupSelect(gi, v)"
                :ref="(el) => setTableRef(gi, el)">
        <el-table-column type="selection" width="44" />
        <el-table-column prop="recordDate" label="日期" width="104" />
        <el-table-column label="归属" width="150" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.id === g.suggestKeepId" type="success" size="small" effect="plain">
              建议保留（最早录入）
            </el-tag>
            <span v-else class="text-light">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="memberName" label="成员" width="86" />
        <el-table-column label="分类" min-width="180">
          <template #default="{ row }">{{ categoryPath(row) }}</template>
        </el-table-column>
        <el-table-column label="金额" width="112" align="right">
          <template #default="{ row }">
            <span class="amount-expense">{{ money(row.amount) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="merchant" label="商家" min-width="110" show-overflow-tooltip />
        <el-table-column prop="payMethod" label="支付方式" width="88" />
        <el-table-column prop="remark" label="备注" min-width="110" show-overflow-tooltip />
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ row }">
            <el-button link type="danger" size="small" @click="onDeleteOne(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Search } from '@element-plus/icons-vue'
import { deleteDuplicates, scanDuplicates } from '../../api/dedup'
import { listMember } from '../../api/member'
import { money } from '../../utils/format'
import { scopeLocked } from '../../utils/auth'

/**
 * 账单查重页（需求第 6 条）。
 *
 * 每组一张表格，用多选框让用户勾选要删的那几条。
 * 「勾选除最早一条外的全部」是最常用的操作，所以单独给一个按钮，
 * 但默认不自动勾 —— 自动勾选等于变相替用户做了删除决定。
 */
const loading = ref(false)
const members = ref([])
const range = ref([])
const params = ref({ dayTolerance: 3, sameMember: true, sameCategory: false, memberId: null })
const result = ref({ groups: [], groupCount: 0, extraCount: 0, extraAmount: 0 })

/** 每组当前勾选的行，按组索引存 */
const selection = ref({})
const tableRefs = {}

const selectedIds = computed(() =>
  Object.values(selection.value).flat().map((r) => r.id)
)

function setTableRef(gi, el) {
  if (el) tableRefs[gi] = el
}

onMounted(async () => {
  if (!scopeLocked.value) {
    members.value = await listMember()
  }
  await scan()
})

async function scan() {
  loading.value = true
  selection.value = {}
  try {
    result.value = await scanDuplicates({
      dayTolerance: params.value.dayTolerance,
      sameMember: params.value.sameMember,
      sameCategory: params.value.sameCategory,
      memberId: params.value.memberId || undefined,
      startDate: range.value?.[0] || undefined,
      endDate: range.value?.[1] || undefined
    })
  } finally {
    loading.value = false
  }
}

function onGroupSelect(gi, rows) {
  selection.value = { ...selection.value, [gi]: rows }
}

function pickAllButFirst(g) {
  const gi = result.value.groups.indexOf(g)
  const table = tableRefs[gi]
  if (!table) return
  table.clearSelection()
  g.records.forEach((r) => {
    if (r.id !== g.suggestKeepId) {
      table.toggleRowSelection(r, true)
    }
  })
}

function categoryPath(row) {
  return [row.categoryLevel >= 3 ? row.rootCategoryName : null, row.parentCategoryName, row.categoryName]
    .filter(Boolean)
    .join(' / ')
}

async function onDelete() {
  const ids = selectedIds.value
  if (!ids.length) {
    ElMessage.warning('请先勾选要删除的流水')
    return
  }
  await ElMessageBox.confirm(
    `确认删除勾选的 ${ids.length} 笔流水？删除后无法恢复。`,
    '删除确认',
    { type: 'warning' }
  )
  const n = await deleteDuplicates(ids)
  ElMessage.success(`已删除 ${n} 笔流水`)
  await scan()
}

async function onDeleteOne(row) {
  await ElMessageBox.confirm(
    `确认删除 ${row.recordDate} 的这笔 ¥${money(row.amount)}？`,
    '删除确认',
    { type: 'warning' }
  )
  await deleteDuplicates([row.id])
  ElMessage.success('删除成功')
  await scan()
}
</script>

<style scoped>
.intro {
  line-height: 1.9;
  margin: -4px 0 14px;
}

.summary {
  display: flex;
  gap: 24px;
  align-items: center;
  flex-wrap: wrap;
  padding: 10px 14px;
  background: #fdf6ec;
  border-radius: 4px;
  font-size: 13px;
  color: var(--gjp-text-light);
}

.summary b {
  color: var(--gjp-text);
  font-size: 15px;
  margin: 0 3px;
}

.summary .picked {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--gjp-text);
}

.trunc {
  margin-top: 10px;
}

.group-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.group-amount {
  font-size: 15px;
  font-weight: 600;
  color: var(--gjp-expense);
}

.group-reason {
  flex: 1;
  min-width: 200px;
  line-height: 1.6;
}
</style>
