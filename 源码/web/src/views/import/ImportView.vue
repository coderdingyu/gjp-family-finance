<template>
  <div>
    <div class="page-card">
      <h3 class="card-title">文件导入</h3>
      <p class="text-light intro">
        一次可上传多个图片、Excel/CSV 或 PDF。系统按文件排队抽取流水，
        你在下方核对后再入库。入库时可直接写入，或合并去重（与账本、本次其他文件重复的只留一份）。
        无关文件（风景照、成绩单、合同等）会被标出来，不会写入账本。
      </p>

      <el-alert
        v-if="config && !config.configured"
        type="warning"
        :closable="false"
        show-icon
        class="mb"
        title="尚未配置 Dify API Key"
        description="图片和扫描件 PDF 需要智能体识别。Excel/CSV 以及带文字的 PDF 仍可用表头规则抽取。配置方法见运行包/dify/导入说明.md。"
      />

      <el-form :inline="true" class="query-bar">
        <el-form-item label="记到成员">
          <el-select v-model="memberId" :disabled="scopeLocked" style="width: 160px">
            <el-option v-for="m in members" :key="m.id" :label="m.memberName" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <span class="text-light">单文件 ≤ 12MB，一次最多 {{ config.maxFiles || 8 }} 个，格式可混搭</span>
        </el-form-item>
      </el-form>

      <el-upload
        ref="uploadRef"
        drag
        multiple
        :auto-upload="false"
        :limit="config.maxFiles || 8"
        accept=".jpg,.jpeg,.png,.webp,.bmp,.xls,.xlsx,.csv,.pdf"
        :on-change="onFileChange"
        :on-remove="onFileChange"
        :on-exceed="onExceed"
        :file-list="fileList"
      >
        <el-icon class="el-icon--upload" :size="40"><UploadFilled /></el-icon>
        <div class="el-upload__text">将账单文件拖到这里，或<em>点击选择</em></div>
        <template #tip>
          <div class="el-upload__tip">支持 jpg / png / webp / bmp / xls / xlsx / csv / pdf</div>
        </template>
      </el-upload>

      <div class="actions">
        <el-button type="primary" :icon="Upload" :loading="uploading" :disabled="!fileList.length" @click="start">
          开始解析
        </el-button>
        <el-button :disabled="!fileList.length || busy" @click="clearFiles">清空</el-button>
      </div>
    </div>

    <div v-if="job" class="page-card">
      <div class="job-head">
        <h3 class="card-title">解析进度</h3>
        <el-tag :type="jobTag.type" effect="plain">{{ jobTag.label }}</el-tag>
        <span class="text-light">{{ job.message || '' }}</span>
      </div>
      <el-progress
        :percentage="jobPercent"
        :status="job.status === 'failed' ? 'exception' : job.status === 'done' ? 'success' : undefined"
        :stroke-width="14"
      />
      <div class="file-list">
        <div v-for="f in job.files" :key="f.id" class="file-row">
          <div class="file-name">
            <el-tag size="small" :type="kindTag(f.kind)">{{ kindLabel(f.kind) }}</el-tag>
            <span>{{ f.originalName }}</span>
            <el-tag size="small" :type="fileTag(f).type">{{ fileTag(f).label }}</el-tag>
          </div>
          <el-progress :percentage="f.progress || 0" :stroke-width="8" :status="fileProgressStatus(f)" />
          <div v-if="f.rejectReason" class="text-light reason">{{ f.rejectReason }}</div>
        </div>
      </div>
    </div>

    <el-alert
      v-for="f in rejectedFiles"
      :key="'rej-' + f.id"
      type="warning"
      :closable="false"
      show-icon
      class="mb-alert"
      :title="'无关文件：' + f.originalName"
      :description="f.rejectReason || '智能体判断这不是家庭账单，已跳过，不会入库。'"
    />

    <div v-if="job && job.items && job.items.length" class="page-card">
      <div class="job-head">
        <h3 class="card-title">待确认流水</h3>
        <span class="text-light">已勾选 {{ selected.length }} / {{ pendingItems.length }} 笔待确认</span>
        <el-button link type="primary" @click="selectAllPending">全选待确认</el-button>
      </div>
      <el-alert
        v-if="job.duplicateCount"
        type="info"
        :closable="false"
        show-icon
        class="mb"
        :title="'检测到 ' + job.duplicateCount + ' 笔疑似重复'"
        description="直接入库会原样写入；合并后入库会与账本、本次其他文件比对，同一天同金额同商家只保留一份。"
      />
      <form class="import-mode" @submit.prevent>
        <fieldset>
          <legend>入库方式</legend>
          <div class="mode-row">
            <el-button
              :disabled="!selected.length || busy || job.status !== 'preview'"
              :loading="confirming && !mergeOnConfirm"
              @click="onConfirm(false)"
            >
              直接入库
            </el-button>
            <el-button
              type="primary"
              :disabled="!selected.length || busy || job.status !== 'preview'"
              :loading="confirming && mergeOnConfirm"
              @click="onConfirm(true)"
            >
              合并后入库
            </el-button>
          </div>
        </fieldset>
      </form>
      <el-table ref="tableRef" :data="job.items" border stripe size="small" @selection-change="onSelect">
        <el-table-column type="selection" width="44" :selectable="rowSelectable" />
        <el-table-column prop="recordDate" label="日期" width="104" />
        <el-table-column label="类型" width="70" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.type === 1 ? 'success' : 'danger'">
              {{ row.type === 1 ? '收入' : '支出' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="categoryName" label="分类" width="110" />
        <el-table-column label="金额" width="110" align="right">
          <template #default="{ row }">
            <span :class="row.type === 1 ? 'amount-income' : 'amount-expense'">{{ money(row.amount) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="merchant" label="商家" min-width="120" show-overflow-tooltip />
        <el-table-column prop="area" label="片区" width="80" />
        <el-table-column prop="payMethod" label="支付" width="80" />
        <el-table-column prop="sourceName" label="来源文件" min-width="140" show-overflow-tooltip />
        <el-table-column label="查重" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.duplicateHint" size="small" type="warning">{{ row.duplicateHint }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="itemTag(row).type">{{ itemTag(row).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="rejectReason" label="说明" min-width="140" show-overflow-tooltip />
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload, UploadFilled } from '@element-plus/icons-vue'
import { confirmImportJob, createImportJob, getImportJob, importConfig } from '../../api/billImport'
import { listMember } from '../../api/member'
import { currentUser, scopeLocked } from '../../utils/auth'
import { money } from '../../utils/format'

const config = ref({ configured: true, maxFiles: 8 })
const members = ref([])
const memberId = ref(currentUser.value.memberId || null)
const fileList = ref([])
const uploadRef = ref()
const tableRef = ref()
const uploading = ref(false)
const confirming = ref(false)
const mergeOnConfirm = ref(false)
const job = ref(null)
const selected = ref([])
let timer = null
let prevStatus = null

const busy = computed(() => ['queued', 'running', 'importing'].includes(job.value?.status))
const pendingItems = computed(() => (job.value?.items || []).filter((i) => i.status === 'pending'))
const rejectedFiles = computed(() => (job.value?.files || []).filter((f) => f.status === 'rejected'))
const jobPercent = computed(() => {
  const j = job.value
  if (!j || !j.totalFiles) return 0
  return Math.min(100, Math.round((j.doneFiles / j.totalFiles) * 100))
})
const jobTag = computed(() => {
  const s = job.value?.status
  if (s === 'queued') return { type: 'info', label: '排队中' }
  if (s === 'running') return { type: '', label: '解析中' }
  if (s === 'preview') return { type: 'warning', label: '待确认' }
  if (s === 'importing') return { type: '', label: '入库中' }
  if (s === 'done') return { type: 'success', label: '已完成' }
  if (s === 'failed') return { type: 'danger', label: '失败' }
  return { type: 'info', label: s || '' }
})

onMounted(async () => {
  config.value = await importConfig()
  members.value = await listMember()
  if (!memberId.value && members.value.length) {
    memberId.value = members.value[0].id
  }
})

onBeforeUnmount(() => stopPoll())

function onFileChange(_, list) {
  fileList.value = list
}

function onExceed() {
  ElMessage.warning(`一次最多 ${config.value.maxFiles || 8} 个文件`)
}

function clearFiles() {
  fileList.value = []
  uploadRef.value?.clearFiles()
}

function rowSelectable(row) {
  return job.value?.status === 'preview' && row.status === 'pending'
}

async function start() {
  if (!fileList.value.length) {
    ElMessage.warning('请先选择文件')
    return
  }
  if (!memberId.value) {
    ElMessage.warning('请选择记账成员')
    return
  }
  uploading.value = true
  try {
    const files = fileList.value.map((f) => f.raw).filter(Boolean)
    job.value = await createImportJob(files, memberId.value)
    selected.value = []
    prevStatus = job.value.status
    if (busy.value) {
      startPoll()
    } else if (job.value.status === 'preview') {
      nextTick(() => selectAllPending())
    }
  } finally {
    uploading.value = false
  }
}

function startPoll() {
  stopPoll()
  if (!busy.value) return
  timer = setInterval(async () => {
    if (!job.value?.id) return
    try {
      job.value = await getImportJob(job.value.id)
      if (job.value.status === 'preview' && (prevStatus === 'queued' || prevStatus === 'running')) {
        nextTick(() => selectAllPending())
      }
      prevStatus = job.value.status
      if (!busy.value) {
        stopPoll()
      }
    } catch (e) {
      stopPoll()
    }
  }, 1000)
}

function stopPoll() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

function onSelect(rows) {
  selected.value = rows.filter((r) => r.status === 'pending')
}

function selectAllPending() {
  const table = tableRef.value
  if (!table) {
    selected.value = [...pendingItems.value]
    return
  }
  pendingItems.value.forEach((row) => table.toggleRowSelection(row, true))
}

async function onConfirm(merge) {
  const ids = selected.value.map((r) => r.id)
  if (!ids.length) {
    ElMessage.warning('请勾选要入库的流水')
    return
  }
  const title = merge ? '合并后入库' : '直接入库'
  const tip = merge
    ? `勾选了 ${ids.length} 笔。合并后会去掉与账本或本次其他文件重复的记录，只保留一份。`
    : `确认把勾选的 ${ids.length} 笔流水全部记到账本？`
  await ElMessageBox.confirm(tip, title, { type: 'warning' })
  mergeOnConfirm.value = merge
  confirming.value = true
  try {
    job.value = await confirmImportJob(job.value.id, ids, merge)
    selected.value = []
    ElMessage.success(job.value.message || '入库完成')
    window.dispatchEvent(new CustomEvent('gjp:record-changed'))
  } finally {
    confirming.value = false
  }
}

function kindLabel(kind) {
  if (kind === 'excel') return '表格'
  if (kind === 'pdf') return 'PDF'
  if (kind === 'image') return '图片'
  return '其他'
}

function kindTag(kind) {
  if (kind === 'excel') return 'success'
  if (kind === 'pdf') return 'danger'
  if (kind === 'image') return 'warning'
  return 'info'
}

function fileTag(f) {
  if (f.status === 'queued') return { type: 'info', label: '排队' }
  if (f.status === 'parsing') return { type: '', label: '解析中' }
  if (f.status === 'ready') return { type: 'success', label: `抽出 ${f.extracted || 0} 笔` }
  if (f.status === 'rejected') return { type: 'warning', label: '无关' }
  if (f.status === 'failed') return { type: 'danger', label: '失败' }
  return { type: 'info', label: f.status }
}

function fileProgressStatus(f) {
  if (f.status === 'failed') return 'exception'
  if (f.status === 'ready' || f.status === 'rejected') return 'success'
  return undefined
}

function itemTag(row) {
  if (row.status === 'pending') return { type: 'warning', label: '待确认' }
  if (row.status === 'accepted') return { type: 'success', label: '已入库' }
  if (row.status === 'skipped') return { type: 'info', label: '已跳过' }
  return { type: 'danger', label: '已丢弃' }
}
</script>

<style scoped>
.intro {
  line-height: 1.9;
  margin: -4px 0 14px;
}

.mb {
  margin-bottom: 14px;
}

.mb-alert {
  margin: 16px 0 0;
}

.actions {
  margin-top: 14px;
}

.job-head {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.job-head .card-title {
  margin: 0;
}

.file-list {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.file-row {
  padding: 10px 12px;
  background: #f8faf9;
  border-radius: 6px;
}

.file-name {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  flex-wrap: wrap;
}

.reason {
  margin-top: 6px;
}

.import-mode {
  margin: 0 0 14px;
}

.import-mode fieldset {
  border: 1px solid #d8e4dc;
  border-radius: 8px;
  padding: 10px 14px 12px;
}

.import-mode legend {
  padding: 0 6px;
  color: #5b6b62;
  font-size: 13px;
}

.mode-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
</style>
