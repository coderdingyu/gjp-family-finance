<template>
  <!-- 悬浮球本体：可拖动，位置记在 localStorage 里 -->
  <div
    v-if="visible"
    class="ball"
    :class="{ dragging, expanded }"
    :style="{ left: pos.x + 'px', top: pos.y + 'px' }"
    @mousedown="onDown"
    @click="onClick"
  >
    <el-icon :size="24"><Plus /></el-icon>
    <span class="ball-tip">快速记账</span>
  </div>

  <!-- 展开后的入口选择 -->
  <div v-if="expanded" class="mask" @click="expanded = false">
    <div class="panel" :style="panelStyle" @click.stop>
      <div class="panel-title">快速记账</div>
      <div class="entries">
        <div class="entry" @click="openManual">
          <el-icon :size="26"><EditPen /></el-icon>
          <div class="entry-name">手动记账</div>
          <div class="entry-desc">逐笔填写，适合单笔消费</div>
        </div>
        <div class="entry" :class="{ disabled: !uploadReady }" @click="openUpload">
          <el-icon :size="26"><UploadFilled /></el-icon>
          <div class="entry-name">文件上传</div>
          <div class="entry-desc">
            {{ uploadReady ? '上传账单图片 / Excel / PDF，智能体自动识别' : '智能体解析功能开发中' }}
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- 手动记账弹窗：字段比流水页的完整表单精简，只保留必填项，追求"三秒记一笔" -->
  <el-dialog v-model="manualDialog" title="快速记账" width="480px" destroy-on-close append-to-body>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
      <el-form-item label="收支类型" prop="type">
        <el-radio-group v-model="form.type" @change="onTypeChange">
          <el-radio-button :value="2">支出</el-radio-button>
          <el-radio-button :value="1">收入</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="金额" prop="amount">
        <el-input-number
          v-model="form.amount"
          :min="0.01"
          :precision="2"
          :step="10"
          size="large"
          controls-position="right"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="分类" prop="categoryPath">
        <el-cascader
          v-model="form.categoryPath"
          :options="categoryOptions"
          :props="cascaderProps"
          placeholder="请选择到末级分类"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="成员" prop="memberId">
        <el-select v-model="form.memberId" :disabled="scopeLocked" style="width: 100%">
          <el-option v-for="m in members" :key="m.id" :label="m.memberName" :value="m.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="日期" prop="recordDate">
        <el-date-picker
          v-model="form.recordDate"
          type="date"
          value-format="YYYY-MM-DD"
          :disabled-date="(d) => d > new Date()"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="商家">
        <el-select
          v-model="form.merchant"
          placeholder="可不填"
          filterable
          allow-create
          default-first-option
          clearable
          style="width: 100%"
        >
          <el-option v-for="m in options.merchants" :key="m" :label="m" :value="m" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="manualDialog = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="onSave">保存并继续</el-button>
      <el-button type="primary" plain :loading="saving" @click="onSaveAndClose">保存并关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { addRecord, recordOptions } from '../api/record'
import { listMember } from '../api/member'
import { treeCategory } from '../api/category'
import { isFamilyUser, scopeLocked, currentUser } from '../utils/auth'
import { today } from '../utils/format'

/**
 * 全局快速记账悬浮球（需求第 3 条）。
 *
 * 挂在 MainLayout 上，所有页面都能用。设计要点：
 *   · 可拖动，位置存 localStorage —— 固定死在右下角会挡住表格的分页器和操作按钮
 *   · 区分"拖动"和"点击"：按下后移动超过 4px 就算拖动，松手不触发展开，
 *     否则用户每次拖完都会弹出面板
 *   · 手动记账表单只留必填项，"保存并继续"支持连续记多笔
 */
const route = useRoute()
const router = useRouter()

/** 文件上传入口：跳到「文件导入」页，由智能体排队解析后再确认入库 */
const uploadReady = ref(true)

// 登录页不显示；系统管理员不记账，也不显示
const visible = computed(() => isFamilyUser.value && route.path !== '/login')

const STORE_KEY = 'gjp_ball_pos'
const pos = ref(loadPos())
const dragging = ref(false)
const expanded = ref(false)

function loadPos() {
  try {
    const saved = JSON.parse(localStorage.getItem(STORE_KEY) || 'null')
    if (saved && Number.isFinite(saved.x) && Number.isFinite(saved.y)) {
      return clamp(saved)
    }
  } catch (e) {
    // 存储被清空或格式损坏时用默认位置，不影响功能
  }
  return { x: window.innerWidth - 96, y: window.innerHeight - 132 }
}

function clamp(p) {
  const size = 56
  return {
    x: Math.min(Math.max(p.x, 8), window.innerWidth - size - 8),
    y: Math.min(Math.max(p.y, 8), window.innerHeight - size - 8)
  }
}

let start = null
let moved = false

function onDown(e) {
  start = { mx: e.clientX, my: e.clientY, x: pos.value.x, y: pos.value.y }
  moved = false
  window.addEventListener('mousemove', onMove)
  window.addEventListener('mouseup', onUp)
  e.preventDefault()
}

function onMove(e) {
  if (!start) return
  const dx = e.clientX - start.mx
  const dy = e.clientY - start.my
  // 移动超过 4px 才算拖动，避免手抖导致点击失效
  if (Math.abs(dx) > 4 || Math.abs(dy) > 4) {
    moved = true
    dragging.value = true
  }
  pos.value = clamp({ x: start.x + dx, y: start.y + dy })
}

function onUp() {
  window.removeEventListener('mousemove', onMove)
  window.removeEventListener('mouseup', onUp)
  if (moved) {
    localStorage.setItem(STORE_KEY, JSON.stringify(pos.value))
  }
  start = null
  dragging.value = false
}

function onClick() {
  if (moved) return   // 刚拖动完，不当作点击
  expanded.value = !expanded.value
}

/** 面板贴着球出现，并保证不超出视口 */
const panelStyle = computed(() => {
  const w = 340
  const h = 200
  let left = pos.value.x - w + 56
  let top = pos.value.y - h - 12
  if (left < 12) left = 12
  if (top < 12) top = pos.value.y + 68
  return { left: left + 'px', top: top + 'px', width: w + 'px' }
})

function onResize() {
  pos.value = clamp(pos.value)
}

onMounted(() => window.addEventListener('resize', onResize))
onBeforeUnmount(() => window.removeEventListener('resize', onResize))

// ---------------- 手动记账 ----------------
const manualDialog = ref(false)
const saving = ref(false)
const formRef = ref()
const members = ref([])
const categoryTree = ref([])
const options = ref({ merchants: [], areas: [], payMethods: [] })
let loaded = false

const cascaderProps = { checkStrictly: false, value: 'id', label: 'categoryName', children: 'children' }
const categoryOptions = computed(() => categoryTree.value.filter((c) => c.type === form.value.type))

const form = ref(emptyForm())

function emptyForm() {
  return {
    type: 2,
    memberId: currentUser.value.memberId || null,
    categoryPath: [],
    amount: null,
    recordDate: today(),
    merchant: null,
    isGift: 0
  }
}

const rules = {
  amount: [{ required: true, message: '请输入金额', trigger: 'blur' }],
  categoryPath: [{ required: true, message: '请选择分类', trigger: 'change' }],
  memberId: [{ required: true, message: '请选择成员', trigger: 'change' }],
  recordDate: [{ required: true, message: '请选择日期', trigger: 'change' }]
}

async function openManual() {
  expanded.value = false
  if (!loaded) {
    // 首次打开才拉基础数据，避免每个页面都为悬浮球多发三个请求
    ;[members.value, categoryTree.value, options.value] = await Promise.all([
      listMember(),
      treeCategory(),
      recordOptions()
    ])
    loaded = true
  }
  form.value = emptyForm()
  manualDialog.value = true
}

function openUpload() {
  if (!uploadReady.value) {
    ElMessage.info('文件上传与智能体解析属于第二批功能，正在开发中')
    return
  }
  expanded.value = false
  router.push('/import')
}

function onTypeChange() {
  form.value.categoryPath = []
}

async function save() {
  await formRef.value.validate()
  const path = form.value.categoryPath
  const payload = { ...form.value, categoryId: path[path.length - 1] }
  delete payload.categoryPath
  saving.value = true
  try {
    await addRecord(payload)
    ElMessage.success(`已记一笔${form.value.type === 1 ? '收入' : '支出'} ¥${form.value.amount}`)
    return true
  } finally {
    saving.value = false
  }
}

async function onSave() {
  if (await save()) {
    // 连续记账时保留类型、成员、日期，只清金额和分类，减少重复操作
    form.value.amount = null
    form.value.categoryPath = []
    form.value.merchant = null
  }
}

async function onSaveAndClose() {
  if (await save()) {
    manualDialog.value = false
    // 通知当前页面刷新。用自定义事件而不是把回调层层传下去，
    // 悬浮球和各业务页面之间就不需要相互认识了。
    window.dispatchEvent(new CustomEvent('gjp:record-changed'))
  }
}
</script>

<style scoped>
.ball {
  position: fixed;
  z-index: 2100;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: linear-gradient(135deg, #2e7d5b, #21a675);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: grab;
  box-shadow: 0 4px 16px rgba(46, 125, 91, 0.4);
  transition: transform 0.15s, box-shadow 0.15s;
  user-select: none;
}

.ball:hover {
  transform: scale(1.08);
  box-shadow: 0 6px 22px rgba(46, 125, 91, 0.5);
}

.ball.dragging {
  cursor: grabbing;
  transition: none;
}

.ball.expanded {
  transform: rotate(45deg);
}

.ball-tip {
  position: absolute;
  right: 64px;
  white-space: nowrap;
  background: rgba(0, 0, 0, 0.72);
  color: #fff;
  font-size: 12px;
  padding: 4px 9px;
  border-radius: 4px;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.15s;
}

.ball:hover .ball-tip {
  opacity: 1;
}

.ball.expanded .ball-tip {
  display: none;
}

.mask {
  position: fixed;
  inset: 0;
  z-index: 2090;
  background: rgba(0, 0, 0, 0.12);
}

.panel {
  position: fixed;
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.18);
  padding: 16px 18px;
}

.panel-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 12px;
  color: var(--gjp-text);
}

.entries {
  display: flex;
  gap: 12px;
}

.entry {
  flex: 1;
  border: 1px solid var(--gjp-border);
  border-radius: 8px;
  padding: 14px 10px;
  text-align: center;
  cursor: pointer;
  transition: all 0.15s;
  color: var(--gjp-primary);
}

.entry:hover {
  border-color: var(--gjp-primary);
  background: #f2faf6;
  transform: translateY(-2px);
}

.entry.disabled {
  color: var(--gjp-text-light);
  cursor: not-allowed;
}

.entry.disabled:hover {
  border-color: var(--gjp-border);
  background: #fafafa;
  transform: none;
}

.entry-name {
  font-size: 14px;
  font-weight: 600;
  margin-top: 7px;
  color: var(--gjp-text);
}

.entry-desc {
  font-size: 11px;
  color: var(--gjp-text-light);
  margin-top: 4px;
  line-height: 1.5;
}
</style>
