<template>
  <div>
    <div class="page-card">
      <h3 class="card-title">文件导入</h3>
      <p class="text-light intro">
        一次可上传多个图片、Excel/CSV 或 PDF。任务记在服务器上，刷新页面不会丢，
        智能体跑完后流水会出现在「待确认」里，不会自动写入账本。
        解析中的任务可以取消整单或跳过还没处理的文件。不同账号互不影响。
      </p>

      <el-alert
        v-if="config && !config.configured"
        type="warning"
        :closable="false"
        show-icon
        class="mb"
        title="尚未配置 Dify API Key"
        description="Excel/CSV 和带文字的 PDF 本机抽取。图片和扫描件请配置 DIFY_API_KEY，由 Dify 文档提取或市场读文件工具处理。见运行包/dify/导入说明.md。"
      />

      <el-form :inline="true" class="query-bar">
        <el-form-item label="记到成员">
          <el-select v-model="memberId" :disabled="scopeLocked" style="width: 160px">
            <el-option v-for="m in members" :key="m.id" :label="m.memberName" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <span class="text-light">
            单文件 ≤ 12MB，一次最多 {{ maxFiles }} 个，已选 {{ fileList.length }} 个，格式可混搭
          </span>
        </el-form-item>
      </el-form>

      <div
        class="dropzone"
        :class="{ 'is-over': dragging, 'is-disabled': uploading }"
        @click="openPicker"
        @dragenter.prevent="onDragEnter"
        @dragover.prevent="onDragEnter"
        @dragleave.prevent="onDragLeave"
        @drop.prevent="onDrop"
      >
        <input
          ref="nativeInput"
          class="file-input"
          type="file"
          multiple
          :accept="acceptAttr"
          :disabled="uploading"
          @click.stop
          @change="onNativePick"
        />
        <el-icon class="drop-icon" :size="40"><UploadFilled /></el-icon>
        <div class="drop-title">将账单文件拖到这里，或<em>一次选择多个文件</em></div>
        <div class="drop-tip">
          支持 jpg / jpeg / png / webp / bmp / xls / xlsx / csv / pdf。
          文件对话框里按住 Command（Windows 用 Ctrl）可连选，最多 {{ maxFiles }} 个。
        </div>
      </div>
      <ul v-if="fileList.length" class="picked-list">
        <li v-for="f in fileList" :key="f.uid">
          <span class="picked-name">{{ f.name }}</span>
          <span class="picked-size">{{ prettySize(f.size) }}</span>
          <el-button link type="primary" :disabled="uploading" @click.stop="removeFile(f)">移除</el-button>
        </li>
      </ul>

      <div class="actions">
        <el-button type="primary" :icon="Upload" :loading="uploading" :disabled="!fileList.length" @click="start">
          开始解析
        </el-button>
        <el-button :disabled="!fileList.length || uploading" @click="clearFiles">清空</el-button>
      </div>
    </div>

    <div v-if="jobs.length" class="page-card">
      <div class="job-head">
        <h3 class="card-title">我的导入任务</h3>
        <span class="text-light">按你自己的导入次数编号，刷新后仍在这里。点一条即可继续看进度或确认入库。</span>
      </div>
      <ul class="job-list">
        <li
          v-for="j in jobs"
          :key="j.id"
          class="job-item"
          :class="{ active: job && job.id === j.id }"
          @click="openJob(j.id)"
        >
          <div class="job-item-main">
            <el-tag size="small" :type="statusMeta(j.status).type">{{ statusMeta(j.status).label }}</el-tag>
            <span>第 {{ j.seqNo || j.id }} 次</span>
            <span class="text-light">{{ j.doneFiles || 0 }}/{{ j.totalFiles || 0 }} 个文件</span>
            <span class="text-light">抽出 {{ j.extracted || 0 }} 笔</span>
          </div>
          <div class="text-light job-item-msg">{{ j.message || fileNames(j) }}</div>
        </li>
      </ul>
    </div>

    <div v-if="job" class="page-card">
      <div class="job-head">
        <h3 class="card-title">{{ job.seqNo ? `第 ${job.seqNo} 次导入` : '解析进度' }}</h3>
        <el-tag :type="jobTag.type" effect="plain">{{ jobTag.label }}</el-tag>
        <span class="text-light">{{ job.message || '' }}</span>
        <el-button
          v-if="canCancelJob"
          link
          type="danger"
          :loading="cancelling"
          @click="onCancelJob"
        >
          取消任务
        </el-button>
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
            <el-button
              v-if="canCancelFile(f)"
              link
              type="danger"
              :loading="cancellingFileId === f.id"
              @click.stop="onCancelFile(f)"
            >
              {{ f.status === 'parsing' ? '停止这个文件' : '取消排队' }}
            </el-button>
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
              :disabled="!selected.length || busy || !canConfirm"
              :loading="confirming && !mergeOnConfirm"
              @click="onConfirm(false)"
            >
              直接入库
            </el-button>
            <el-button
              type="primary"
              :disabled="!selected.length || busy || !canConfirm"
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
        <el-table-column prop="orderNo" label="订单号" min-width="120" show-overflow-tooltip />
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
import {
  cancelImportFile,
  cancelImportJob,
  confirmImportJob,
  createImportJob,
  getImportJob,
  importConfig,
  listImportJobs
} from '../../api/billImport'
import { listMember } from '../../api/member'
import { currentUser, scopeLocked } from '../../utils/auth'
import { money } from '../../utils/format'

const ACCEPT = '.jpg,.jpeg,.png,.webp,.bmp,.xls,.xlsx,.csv,.pdf'
const config = ref({ configured: true, maxFiles: 10 })
const members = ref([])
const memberId = ref(currentUser.value.memberId || null)
const fileList = ref([])
const nativeInput = ref()
const tableRef = ref()
const uploading = ref(false)
const confirming = ref(false)
const mergeOnConfirm = ref(false)
const dragging = ref(false)
const job = ref(null)
const jobs = ref([])
const selected = ref([])
const cancelling = ref(false)
const cancellingFileId = ref(null)
const JOB_STORE = 'gjp-import-open-job'
let timer = null
let prevStatus = null
let dragDepth = 0
let fileSeq = 1

const maxFiles = computed(() => config.value.maxFiles || 10)
const acceptAttr = ACCEPT

const busy = computed(() => ['queued', 'running', 'importing'].includes(job.value?.status))
const canCancelJob = computed(() => ['queued', 'running'].includes(job.value?.status))
const pendingItems = computed(() => (job.value?.items || []).filter((i) => i.status === 'pending'))
const canConfirm = computed(() => pendingItems.value.length > 0 && !busy.value && job.value?.status !== 'failed')
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
  if (s === 'cancelled') return { type: 'info', label: '已取消' }
  return { type: 'info', label: s || '' }
})

onMounted(async () => {
  config.value = await importConfig()
  members.value = await listMember()
  if (!memberId.value && members.value.length) {
    memberId.value = members.value[0].id
  }
  await refreshJobs()
  const stored = Number(localStorage.getItem(JOB_STORE) || 0)
  const active = jobs.value.find((j) => ['queued', 'running', 'importing', 'preview'].includes(j.status))
  const prefer = jobs.value.find((j) => j.id === stored) || active || jobs.value[0]
  if (prefer) {
    await openJob(prefer.id)
  }
})

onBeforeUnmount(() => stopPoll())

function openPicker() {
  if (uploading.value) return
  nativeInput.value?.click()
}

function onDragEnter() {
  if (uploading.value) return
  dragDepth += 1
  dragging.value = true
}

function onDragLeave() {
  dragDepth = Math.max(0, dragDepth - 1)
  if (dragDepth === 0) dragging.value = false
}

function onDrop(e) {
  dragDepth = 0
  dragging.value = false
  if (uploading.value) return
  addFiles(e.dataTransfer?.files)
}

function onNativePick(e) {
  addFiles(e.target.files)
  e.target.value = ''
}

function addFiles(list) {
  if (!list || !list.length) return
  const incoming = Array.from(list)
  const room = maxFiles.value - fileList.value.length
  if (room <= 0) {
    ElMessage.warning(`一次最多 ${maxFiles.value} 个文件，请先移除再选`)
    return
  }
  const take = incoming.slice(0, room)
  const skipped = incoming.length - take.length
  for (const raw of take) {
    const dup = fileList.value.some((f) => f.name === raw.name && f.size === raw.size)
    if (dup) continue
    fileList.value.push({
      uid: fileSeq++,
      name: raw.name,
      size: raw.size,
      raw
    })
  }
  if (skipped > 0) {
    ElMessage.warning(`一次最多 ${maxFiles.value} 个，多出的 ${skipped} 个未加入`)
  }
}

function removeFile(file) {
  fileList.value = fileList.value.filter((f) => f.uid !== file.uid)
}

function clearFiles() {
  fileList.value = []
  if (nativeInput.value) nativeInput.value.value = ''
}

function prettySize(n) {
  if (!n && n !== 0) return ''
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / 1024 / 1024).toFixed(1)} MB`
}

function rowSelectable(row) {
  return canConfirm.value && row.status === 'pending'
}

async function refreshJobs() {
  jobs.value = await listImportJobs()
}

async function openJob(id) {
  try {
    job.value = await getImportJob(id)
  } catch {
    return
  }
  localStorage.setItem(JOB_STORE, String(id))
  selected.value = []
  prevStatus = job.value.status
  if (busy.value) {
    startPoll()
  } else {
    stopPoll()
    if (canConfirm.value) {
      nextTick(() => selectAllPending())
    }
  }
}

function fileNames(j) {
  return (j.files || []).map((f) => f.originalName).filter(Boolean).slice(0, 3).join('、')
}

function statusMeta(s) {
  if (s === 'queued') return { type: 'info', label: '排队中' }
  if (s === 'running') return { type: '', label: '解析中' }
  if (s === 'preview') return { type: 'warning', label: '待确认' }
  if (s === 'importing') return { type: '', label: '入库中' }
  if (s === 'done') return { type: 'success', label: '已完成' }
  if (s === 'failed') return { type: 'danger', label: '失败' }
  if (s === 'cancelled') return { type: 'info', label: '已取消' }
  return { type: 'info', label: s || '' }
}

function canCancelFile(f) {
  return ['queued', 'parsing'].includes(f.status) && ['queued', 'running'].includes(job.value?.status)
}

async function onCancelJob() {
  await ElMessageBox.confirm('停止这个任务？已经抽出的流水还在，还没处理完的文件会停掉。', '取消任务', {
    type: 'warning'
  })
  cancelling.value = true
  try {
    job.value = await cancelImportJob(job.value.id)
    await refreshJobs()
    stopPoll()
    ElMessage.success(job.value.message || '已取消')
  } finally {
    cancelling.value = false
  }
}

async function onCancelFile(f) {
  cancellingFileId.value = f.id
  try {
    job.value = await cancelImportFile(job.value.id, f.id)
    await refreshJobs()
    ElMessage.success('已取消该文件')
  } finally {
    cancellingFileId.value = null
  }
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
    localStorage.setItem(JOB_STORE, String(job.value.id))
    selected.value = []
    prevStatus = job.value.status
    await refreshJobs()
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
      await refreshJobs()
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
  if (f.status === 'cancelled') return { type: 'info', label: '已取消' }
  return { type: 'info', label: f.status }
}

function fileProgressStatus(f) {
  if (f.status === 'failed') return 'exception'
  if (f.status === 'ready' || f.status === 'rejected' || f.status === 'cancelled') return 'success'
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

.dropzone {
  position: relative;
  border: 1px dashed #c3d4c8;
  border-radius: 8px;
  background: #f7faf8;
  padding: 36px 20px 28px;
  text-align: center;
  cursor: pointer;
}

.dropzone.is-over {
  border-color: #3d8b5e;
  background: #eef6f0;
}

.dropzone.is-disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.file-input {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.drop-icon {
  color: #7a8f80;
}

.drop-title {
  margin-top: 8px;
  color: #3c4a42;
}

.drop-title em {
  font-style: normal;
  color: #2f7d4f;
}

.drop-tip {
  margin-top: 8px;
  font-size: 12px;
  color: #7a8f80;
  line-height: 1.7;
}

.picked-list {
  margin: 12px 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.picked-list li {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 10px;
  background: #f8faf9;
  border-radius: 6px;
}

.picked-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.picked-size {
  color: #7a8f80;
  font-size: 12px;
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

.job-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.job-item {
  padding: 10px 12px;
  background: #f8faf9;
  border-radius: 6px;
  cursor: pointer;
  border: 1px solid transparent;
}

.job-item.active {
  border-color: #3d8b5e;
  background: #eef6f0;
}

.job-item-main {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.job-item-msg {
  margin-top: 4px;
  font-size: 12px;
}
</style>
