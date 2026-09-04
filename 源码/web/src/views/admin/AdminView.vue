<template>
  <div v-loading="loading">
    <!-- 运行状态 -->
    <el-row :gutter="14" class="cards">
      <el-col :md="6" :sm="12">
        <StatCard label="内存占用" :value="`${rt.memoryUsedMb ?? '-'} MB`" prefix="" raw color="var(--gjp-count)"
                  :sub="`共 ${rt.memoryMaxMb} MB · 使用率 ${rt.memoryUsedRate}%`" />
      </el-col>
      <el-col :md="6" :sm="12">
        <StatCard label="已运行" :value="rt.uptime" prefix="" raw color="var(--gjp-income)"
                  :sub="`启动于 ${rt.startTime}`" />
      </el-col>
      <el-col :md="6" :sm="12">
        <StatCard label="操作日志" :value="scale.logCount" prefix="" raw color="var(--gjp-balance)"
                  :sub="`失败 ${health.failedCount} 条 · 失败率 ${health.failRate}%`" />
      </el-col>
      <el-col :md="6" :sm="12">
        <StatCard label="运行状态" :value="health.status" prefix="" raw
                  :color="health.status === '正常' ? 'var(--gjp-income)' : 'var(--gjp-expense)'"
                  :sub="`${scale.familyCount} 个家庭 · ${scale.userCount} 个账号`" />
      </el-col>
    </el-row>

    <el-row :gutter="14">
      <el-col :md="14">
        <div class="page-card">
          <h3 class="card-title">近 14 天操作量</h3>
          <EChart :option="dayOption" :empty="!overview.logByDay?.length" height="280px"
                  empty-text="暂无操作记录" />
        </div>
      </el-col>
      <el-col :md="10">
        <div class="page-card">
          <h3 class="card-title">运行环境</h3>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="Java 版本">{{ rt.javaVersion }}</el-descriptions-item>
            <el-descriptions-item label="操作系统">{{ rt.osName }}</el-descriptions-item>
            <el-descriptions-item label="CPU 核心">{{ rt.cpuCores }}</el-descriptions-item>
            <el-descriptions-item label="内存">
              {{ rt.memoryUsedMb }} / {{ rt.memoryMaxMb }} MB
              <el-progress :percentage="Number(rt.memoryUsedRate) || 0" :stroke-width="8"
                           :show-text="false" style="margin-top: 5px" />
            </el-descriptions-item>
            <el-descriptions-item label="已禁用账号">
              {{ scale.disabledUserCount }} 个
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="14">
      <el-col :md="14">
        <div class="page-card">
          <h3 class="card-title">各家庭规模</h3>
          <el-table :data="families" border stripe size="small">
            <el-table-column prop="familyName" label="家庭" min-width="120" />
            <el-table-column prop="userCount" label="账号" width="76" align="center" />
            <el-table-column prop="memberCount" label="成员" width="76" align="center" />
            <el-table-column prop="recordCount" label="流水" width="86" align="center" />
            <el-table-column prop="assetCount" label="资产" width="76" align="center" />
            <el-table-column prop="createTime" label="创建时间" width="170" />
          </el-table>
          <p class="text-light note">
            管理员只能看到规模指标，看不到任何家庭的具体账单金额与消费内容 ——
            运维排查不需要这些数据，权限按「完成工作所需的最小范围」给。
          </p>
        </div>
      </el-col>
      <el-col :md="10">
        <div class="page-card">
          <h3 class="card-title">日志模块分布</h3>
          <EChart :option="moduleOption" :empty="!overview.logByModule?.length" height="280px"
                  empty-text="暂无操作记录" />
        </div>
      </el-col>
    </el-row>

    <!-- 账号管理 -->
    <div class="page-card">
      <div class="bar">
        <h3 class="card-title" style="margin: 0">账号管理</h3>
        <div class="tools">
          <el-input v-model="userKeyword" placeholder="账号 / 姓名 / 家庭" clearable
                    style="width: 200px" @keyup.enter="loadUsers" />
          <el-select v-model="userRole" placeholder="全部角色" clearable style="width: 130px"
                     @change="loadUsers">
            <el-option label="普通成员" :value="0" />
            <el-option label="户主" :value="1" />
            <el-option label="系统管理员" :value="2" />
          </el-select>
          <el-button type="primary" plain :icon="Search" @click="loadUsers">查询</el-button>
          <el-button :icon="Lock" @click="pwdDialog = true">修改我的密码</el-button>
        </div>
      </div>
      <el-table :data="users" border stripe size="small">
        <el-table-column prop="username" label="账号" width="130" />
        <el-table-column prop="realName" label="姓名" width="110" />
        <el-table-column label="家庭" width="110">
          <template #default="{ row }">{{ row.familyName || '—' }}</template>
        </el-table-column>
        <el-table-column label="绑定成员" width="110">
          <template #default="{ row }">{{ row.memberName || '—' }}</template>
        </el-table-column>
        <el-table-column label="角色" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="roleTag(row.role)" size="small" effect="dark">{{ roleName(row.role) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="86" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small" effect="plain">
              {{ row.status === 1 ? '正常' : '已禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastLogin" label="最后登录" min-width="170">
          <template #default="{ row }">{{ row.lastLogin || '从未登录' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <template v-if="row.role === 2">
              <span class="text-light">管理员账号</span>
            </template>
            <template v-else>
              <el-button link type="primary" size="small" @click="openReset(row)">重置密码</el-button>
              <el-button link :type="row.status === 1 ? 'danger' : 'success'" size="small"
                         @click="onToggle(row)">
                {{ row.status === 1 ? '禁用' : '启用' }}
              </el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 重置他人密码 -->
    <el-dialog v-model="resetDialog" title="重置密码" width="380px" destroy-on-close>
      <el-form ref="resetRef" :model="resetForm" :rules="pwdRules" label-width="88px">
        <el-form-item label="账号">
          <el-input :model-value="resetForm.username" disabled />
        </el-form-item>
        <el-form-item label="新密码" prop="password">
          <el-input v-model="resetForm.password" placeholder="6-20 个字符" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onReset">确认重置</el-button>
      </template>
    </el-dialog>

    <!-- 修改自己的密码 -->
    <el-dialog v-model="pwdDialog" title="修改我的密码" width="380px" destroy-on-close>
      <el-form ref="pwdRef" :model="pwdForm" :rules="ownPwdRules" label-width="88px">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="pwdForm.oldPassword" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="pwdForm.newPassword" placeholder="6-20 个字符" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onChangeOwn">确认修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Lock, Search } from '@element-plus/icons-vue'
import EChart from '../../components/EChart.vue'
import StatCard from '../../components/StatCard.vue'
import {
  adminChangeOwnPassword, adminFamilies, adminOverview, adminResetPassword,
  adminToggleStatus, adminUsers
} from '../../api/admin'
import { CHART_COLORS, TONE } from '../../utils/format'

/**
 * 系统维护界面（需求第 8 条）。
 *
 * 内容围绕"这个网站现在是否正常"组织：内存与运行时长看进程健康，
 * 日志失败率看业务是否频繁出错，操作量趋势看是否有异常访问，
 * 家庭规模看数据增长。加上账号处理（重置密码、封禁）就覆盖了日常维护动作。
 */
const loading = ref(false)
const saving = ref(false)
const overview = ref({})
const families = ref([])
const users = ref([])
const userKeyword = ref('')
const userRole = ref(null)

const rt = computed(() => overview.value.runtime || {})
const scale = computed(() => overview.value.scale || {})
const health = computed(() => overview.value.health || {})

const resetDialog = ref(false)
const resetRef = ref()
const resetForm = ref({ userId: null, username: '', password: '' })

const pwdDialog = ref(false)
const pwdRef = ref()
const pwdForm = ref({ oldPassword: '', newPassword: '' })

const pwdRules = {
  password: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度 6-20 个字符', trigger: 'blur' }
  ]
}
const ownPwdRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度 6-20 个字符', trigger: 'blur' }
  ]
}

onMounted(async () => {
  loading.value = true
  try {
    ;[overview.value, families.value] = await Promise.all([adminOverview(), adminFamilies()])
    await loadUsers()
  } finally {
    loading.value = false
  }
})

async function loadUsers() {
  users.value = await adminUsers({
    keyword: userKeyword.value || undefined,
    role: userRole.value ?? undefined
  })
}

function roleName(role) {
  return role === 2 ? '系统管理员' : role === 1 ? '户主' : '普通成员'
}

function roleTag(role) {
  return role === 2 ? 'danger' : role === 1 ? 'success' : 'info'
}

const dayOption = computed(() => {
  const rows = overview.value.logByDay || []
  return {
    color: [TONE.primary],
    tooltip: { trigger: 'axis' },
    grid: { left: 46, right: 24, top: 20, bottom: 30 },
    xAxis: { type: 'category', data: rows.map((r) => r.name) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        type: 'bar',
        barMaxWidth: 26,
        data: rows.map((r) => Number(r.value)),
        label: { show: true, position: 'top', fontSize: 11 }
      }
    ]
  }
})

const moduleOption = computed(() => {
  const rows = overview.value.logByModule || []
  const total = rows.reduce((s, r) => s + Number(r.value), 0)
  return {
    color: CHART_COLORS,
    tooltip: { trigger: 'item', formatter: (p) => `${p.name}<br/>${p.value} 条（${p.percent}%）` },
    legend: {
      type: 'scroll',
      orient: 'vertical',
      right: 4,
      top: 14,
      bottom: 14,
      itemWidth: 10,
      itemHeight: 10,
      formatter: (n) => {
        const row = rows.find((r) => r.name === n)
        const pct = total === 0 ? 0 : ((Number(row?.value || 0) / total) * 100).toFixed(1)
        return `${n}  ${pct}%`
      }
    },
    series: [
      {
        type: 'pie',
        radius: ['45%', '70%'],
        center: ['32%', '50%'],
        itemStyle: { borderColor: '#fff', borderWidth: 2 },
        label: { show: false },
        labelLine: { show: false },
        emphasis: { label: { show: true, fontSize: 14, fontWeight: 600, formatter: '{d}%' } },
        data: rows.map((r) => ({ name: r.name, value: Number(r.value) }))
      }
    ]
  }
})

function openReset(row) {
  resetForm.value = { userId: row.id, username: row.username, password: '' }
  resetDialog.value = true
}

async function onReset() {
  await resetRef.value.validate()
  saving.value = true
  try {
    await adminResetPassword(resetForm.value.userId, resetForm.value.password)
    ElMessage.success('密码已重置')
    resetDialog.value = false
  } finally {
    saving.value = false
  }
}

async function onToggle(row) {
  const next = row.status === 1 ? 0 : 1
  await ElMessageBox.confirm(
    `确认${next === 1 ? '启用' : '禁用'}账号【${row.username}】？`,
    '提示',
    { type: 'warning' }
  )
  await adminToggleStatus(row.id, next)
  ElMessage.success(next === 1 ? '已启用' : '已禁用')
  await loadUsers()
  overview.value = await adminOverview()
}

async function onChangeOwn() {
  await pwdRef.value.validate()
  saving.value = true
  try {
    await adminChangeOwnPassword(pwdForm.value.oldPassword, pwdForm.value.newPassword)
    ElMessage.success('密码修改成功，下次登录请使用新密码')
    pwdDialog.value = false
    pwdForm.value = { oldPassword: '', newPassword: '' }
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.cards .el-col {
  margin-bottom: 14px;
}

.el-row + .el-row .page-card,
.el-row + .page-card {
  margin-top: 0;
}

.page-card {
  margin-bottom: 14px;
}

.bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.tools {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.note {
  line-height: 1.8;
  margin: 10px 0 0;
}
</style>
