<template>
  <div>
    <div class="page-card">
      <h3 class="card-title">成员登录账号</h3>
      <p class="text-light intro">
        为家庭成员开通登录账号后，他们可以自己记账，但<b>只能看到自己名下的流水与统计</b>；
        资产负债、成员管理、分类维护仍只有户主可用。一个家庭只有一个户主。
      </p>

      <el-table :data="accounts" v-loading="loading" border stripe size="small">
        <el-table-column prop="username" label="登录账号" width="140" />
        <el-table-column prop="realName" label="姓名" width="110" />
        <el-table-column label="绑定成员" width="120">
          <template #default="{ row }">
            {{ row.memberName || '—' }}
          </template>
        </el-table-column>
        <el-table-column label="角色" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.role === 1 ? 'success' : 'info'" size="small" effect="dark">
              {{ row.role === 1 ? '户主' : '普通成员' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="86" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small" effect="plain">
              {{ row.status === 1 ? '正常' : '已禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastLogin" label="最后登录" width="170">
          <template #default="{ row }">
            {{ row.lastLogin || '从未登录' }}
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" min-width="170" />
        <el-table-column label="操作" width="190" align="center" fixed="right">
          <template #default="{ row }">
            <template v-if="row.role === 1">
              <span class="text-light">户主账号不可操作</span>
            </template>
            <template v-else>
              <el-button link type="primary" size="small" @click="openReset(row)">重置密码</el-button>
              <el-button
                link
                :type="row.status === 1 ? 'danger' : 'success'"
                size="small"
                @click="onToggle(row)"
              >
                {{ row.status === 1 ? '禁用' : '启用' }}
              </el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="page-card">
      <h3 class="card-title">尚未开通账号的成员</h3>
      <el-empty v-if="!pendingMembers.length" description="全部成员都已开通登录账号" :image-size="80" />
      <el-table v-else :data="pendingMembers" border stripe size="small">
        <el-table-column prop="memberName" label="成员姓名" width="150" />
        <el-table-column label="家庭关系" width="120">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.relation || '未填写' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="月度预算" width="140" align="right">
          <template #default="{ row }">
            <span v-if="Number(row.monthlyBudget) > 0">¥{{ money(row.monthlyBudget) }}</span>
            <span v-else class="text-light">未设置</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="140">
          <template #default="{ row }">
            <el-button type="primary" size="small" :icon="Plus" @click="openCreate(row)">
              开通账号
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 开通账号 -->
    <el-dialog v-model="createDialog" title="开通登录账号" width="420px" destroy-on-close>
      <el-form ref="createRef" :model="createForm" :rules="createRules" label-width="88px">
        <el-form-item label="家庭成员">
          <el-input :model-value="createForm.memberName" disabled />
        </el-form-item>
        <el-form-item label="登录账号" prop="username">
          <el-input v-model="createForm.username" placeholder="3-20 个字符，全系统唯一" />
        </el-form-item>
        <el-form-item label="初始密码" prop="password">
          <el-input v-model="createForm.password" placeholder="6-20 个字符" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onCreate">开通</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码 -->
    <el-dialog v-model="resetDialog" title="重置密码" width="380px" destroy-on-close>
      <el-form ref="resetRef" :model="resetForm" :rules="resetRules" label-width="88px">
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
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  createAccount, listAccounts, listMember, resetAccountPassword, toggleAccountStatus
} from '../../api/member'
import { money } from '../../utils/format'

/**
 * 成员账号管理（户主专用）。
 * 这一页是权限模型能落地的前提：不给成员开账号，"分级可见"就无从体现。
 */
const loading = ref(false)
const saving = ref(false)
const accounts = ref([])
const members = ref([])

/** 还没有登录账号的成员 */
const pendingMembers = computed(() => {
  const bound = new Set(accounts.value.map((a) => a.memberId).filter(Boolean))
  return members.value.filter((m) => !bound.has(m.id))
})

const createDialog = ref(false)
const createRef = ref()
const createForm = ref({ memberId: null, memberName: '', username: '', password: '' })
const createRules = {
  username: [
    { required: true, message: '请输入登录账号', trigger: 'blur' },
    { min: 3, max: 20, message: '账号长度 3-20 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入初始密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度 6-20 个字符', trigger: 'blur' }
  ]
}

const resetDialog = ref(false)
const resetRef = ref()
const resetForm = ref({ userId: null, username: '', password: '' })
const resetRules = {
  password: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度 6-20 个字符', trigger: 'blur' }
  ]
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    ;[accounts.value, members.value] = await Promise.all([listAccounts(), listMember()])
  } finally {
    loading.value = false
  }
}

function openCreate(member) {
  createForm.value = {
    memberId: member.id,
    memberName: member.memberName,
    // 给一个默认账号建议，减少户主的输入
    username: '',
    password: ''
  }
  createDialog.value = true
}

async function onCreate() {
  await createRef.value.validate()
  saving.value = true
  try {
    await createAccount(createForm.value.memberId, createForm.value.username, createForm.value.password)
    ElMessage.success(`已为【${createForm.value.memberName}】开通账号`)
    createDialog.value = false
    await load()
  } finally {
    saving.value = false
  }
}

function openReset(row) {
  resetForm.value = { userId: row.id, username: row.username, password: '' }
  resetDialog.value = true
}

async function onReset() {
  await resetRef.value.validate()
  saving.value = true
  try {
    await resetAccountPassword(resetForm.value.userId, resetForm.value.password)
    ElMessage.success('密码已重置')
    resetDialog.value = false
  } finally {
    saving.value = false
  }
}

async function onToggle(row) {
  const next = row.status === 1 ? 0 : 1
  await ElMessageBox.confirm(
    `确认${next === 1 ? '启用' : '禁用'}账号【${row.username}】？${next === 0 ? '禁用后该账号无法登录。' : ''}`,
    '提示',
    { type: 'warning' }
  )
  await toggleAccountStatus(row.id, next)
  ElMessage.success(next === 1 ? '已启用' : '已禁用')
  await load()
}
</script>

<style scoped>
.intro {
  line-height: 1.9;
  margin: -4px 0 14px;
}
</style>
