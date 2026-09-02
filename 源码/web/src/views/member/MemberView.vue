<template>
  <div class="page-card">
    <h3 class="card-title">成员与账号</h3>
    <div class="bar">
      <span class="text-light">
        成员是收支数据的归属单位，登录账号决定成员能否自行登录。
        <template v-if="isOwner">删除账号不会删除成员档案和流水。</template>
        <template v-else>当前仅显示您自己的成员资料，页面为只读。</template>
      </span>
      <el-button v-if="isOwner" type="primary" :icon="Plus" :disabled="busy" @click="openAdd">
        新增成员
      </el-button>
    </div>

    <el-table :data="rows" v-loading="loading" border stripe size="small">
      <el-table-column type="index" label="#" width="50" align="center" />
      <el-table-column prop="memberName" label="成员姓名" min-width="110" fixed="left" />
      <el-table-column label="家庭关系" width="100">
        <template #default="{ row }">
          <el-tag size="small" effect="plain">{{ row.relation || '未填写' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="月度预算" width="125" align="right">
        <template #default="{ row }">
          <span v-if="Number(row.monthlyBudget) > 0">¥{{ money(row.monthlyBudget) }}</span>
          <span v-else class="text-light">未设置</span>
        </template>
      </el-table-column>

      <template v-if="isOwner">
        <el-table-column label="已开通账号" width="105" align="center">
          <template #default="{ row }">
            <el-tag :type="row.account ? 'success' : 'info'" size="small" effect="plain">
              {{ row.account ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="登录账号" width="130">
          <template #default="{ row }">{{ row.account?.username || '—' }}</template>
        </el-table-column>
        <el-table-column label="账号角色" width="105" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.account" :type="row.account.role === ROLE.OWNER ? 'success' : 'info'" size="small">
              {{ accountRole(row.account) }}
            </el-tag>
            <span v-else class="text-light">—</span>
          </template>
        </el-table-column>
        <el-table-column label="账号状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag
              v-if="row.account"
              :type="row.account.status === 1 ? 'success' : 'danger'"
              size="small"
              effect="plain"
            >
              {{ row.account.status === 1 ? '正常' : '已禁用' }}
            </el-tag>
            <span v-else class="text-light">—</span>
          </template>
        </el-table-column>
        <el-table-column label="最后登录" width="170">
          <template #default="{ row }">{{ row.account?.lastLogin || '从未登录' }}</template>
        </el-table-column>
      </template>

      <el-table-column prop="createTime" label="成员创建时间" width="170" />
      <el-table-column v-if="isOwner" label="成员操作" width="145" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" :disabled="busy" @click="openEdit(row)">编辑成员</el-button>
          <el-button
            v-if="row.account?.role !== ROLE.OWNER"
            link
            type="danger"
            size="small"
            :loading="actionKey === `member-delete-${row.id}`"
            :disabled="busy"
            @click="onDeleteMember(row)"
          >
            删除成员
          </el-button>
        </template>
      </el-table-column>
      <el-table-column v-if="isOwner" label="账号操作" width="285" align="center" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="!row.account"
            type="primary"
            size="small"
            :disabled="busy"
            @click="openCreate(row)"
          >
            开通账号
          </el-button>
          <span v-else-if="row.account.role === ROLE.OWNER" class="text-light">户主账号不可禁用或删除</span>
          <template v-else>
            <el-button link type="primary" size="small" :disabled="busy" @click="openReset(row.account)">
              重置密码
            </el-button>
            <el-button
              link
              :type="row.account.status === 1 ? 'danger' : 'success'"
              size="small"
              :loading="actionKey === `account-toggle-${row.account.id}`"
              :disabled="busy"
              @click="onToggle(row.account)"
            >
              {{ row.account.status === 1 ? '禁用账号' : '启用账号' }}
            </el-button>
            <el-button
              link
              type="danger"
              size="small"
              :loading="actionKey === `account-delete-${row.account.id}`"
              :disabled="busy"
              @click="onDeleteAccount(row.account)"
            >
              删除账号
            </el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="memberDialog" :title="memberForm.id ? '编辑成员' : '新增成员'" width="440px" destroy-on-close>
      <el-form ref="memberRef" :model="memberForm" :rules="memberRules" label-width="88px">
        <el-form-item label="姓名" prop="memberName">
          <el-input v-model="memberForm.memberName" placeholder="请输入成员姓名" maxlength="20" />
        </el-form-item>
        <el-form-item label="家庭关系" prop="relation">
          <el-select v-model="memberForm.relation" placeholder="请选择" style="width: 100%">
            <el-option v-for="relation in relations" :key="relation" :label="relation" :value="relation" />
          </el-select>
        </el-form-item>
        <el-form-item label="月度预算">
          <el-input-number
            v-model="memberForm.monthlyBudget"
            :min="0"
            :precision="2"
            :step="500"
            controls-position="right"
            style="width: 100%"
          />
          <div class="text-light">填 0 表示不做预算控制</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="memberDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSaveMember">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="createDialog" title="开通登录账号" width="420px" destroy-on-close>
      <el-form ref="createRef" :model="createForm" :rules="createRules" label-width="88px">
        <el-form-item label="家庭成员">
          <el-input :model-value="createForm.memberName" disabled />
        </el-form-item>
        <el-form-item label="登录账号" prop="username">
          <el-input v-model="createForm.username" placeholder="3-20 个字符，全系统唯一" />
        </el-form-item>
        <el-form-item label="初始密码" prop="password">
          <el-input v-model="createForm.password" type="password" placeholder="6-20 个字符" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onCreateAccount">开通</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resetDialog" title="重置密码" width="380px" destroy-on-close>
      <el-form ref="resetRef" :model="resetForm" :rules="resetRules" label-width="88px">
        <el-form-item label="账号">
          <el-input :model-value="resetForm.username" disabled />
        </el-form-item>
        <el-form-item label="新密码" prop="password">
          <el-input v-model="resetForm.password" type="password" placeholder="6-20 个字符" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onResetPassword">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  addMember,
  createAccount,
  deleteAccount,
  deleteMember,
  listAccounts,
  listMember,
  resetAccountPassword,
  toggleAccountStatus,
  updateMember
} from '../../api/member'
import { isOwner, ROLE } from '../../utils/auth'
import { money } from '../../utils/format'

const relations = ['本人', '配偶', '子女', '父母', '其他']
const loading = ref(false)
const saving = ref(false)
const actionKey = ref('')
const members = ref([])
const accounts = ref([])
const busy = computed(() => saving.value || !!actionKey.value)

const rows = computed(() => {
  const byMemberId = new Map(accounts.value.map((account) => [String(account.memberId), account]))
  return members.value.map((member) => ({
    ...member,
    account: byMemberId.get(String(member.id)) || null
  }))
})

const memberDialog = ref(false)
const memberRef = ref()
const memberForm = ref(emptyMember())
const memberRules = {
  memberName: [{ required: true, message: '请输入成员姓名', trigger: 'blur' }],
  relation: [{ required: true, message: '请选择家庭关系', trigger: 'change' }]
}

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

function emptyMember() {
  return { id: null, memberName: '', relation: '', monthlyBudget: 0 }
}

function accountRole(account) {
  return account.role === ROLE.OWNER ? '户主' : '普通成员'
}

async function load() {
  loading.value = true
  try {
    if (isOwner.value) {
      ;[members.value, accounts.value] = await Promise.all([listMember(), listAccounts()])
    } else {
      members.value = await listMember()
      accounts.value = []
    }
  } finally {
    loading.value = false
  }
}

function openAdd() {
  memberForm.value = emptyMember()
  memberDialog.value = true
}

function openEdit(row) {
  memberForm.value = {
    id: row.id,
    memberName: row.memberName,
    relation: row.relation,
    monthlyBudget: Number(row.monthlyBudget || 0)
  }
  memberDialog.value = true
}

async function onSaveMember() {
  await memberRef.value.validate()
  saving.value = true
  try {
    if (memberForm.value.id) {
      await updateMember(memberForm.value.id, memberForm.value)
      ElMessage.success('成员资料已更新')
    } else {
      await addMember(memberForm.value)
      ElMessage.success('成员已新增')
    }
    memberDialog.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function onDeleteMember(row) {
  if (row.account) {
    ElMessage.warning('请先删除成员登录账号，再删除成员档案。')
    return
  }
  await ElMessageBox.confirm(
    `确认删除成员档案【${row.memberName}】？名下存在流水时服务端会拒绝删除。`,
    '删除成员档案',
    { type: 'warning' }
  )
  actionKey.value = `member-delete-${row.id}`
  try {
    await deleteMember(row.id)
    ElMessage.success('成员档案已删除')
    await load()
  } finally {
    actionKey.value = ''
  }
}

function openCreate(row) {
  createForm.value = { memberId: row.id, memberName: row.memberName, username: '', password: '' }
  createDialog.value = true
}

async function onCreateAccount() {
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

function openReset(account) {
  resetForm.value = { userId: account.id, username: account.username, password: '' }
  resetDialog.value = true
}

async function onResetPassword() {
  await resetRef.value.validate()
  saving.value = true
  try {
    await resetAccountPassword(resetForm.value.userId, resetForm.value.password)
    ElMessage.success('密码已重置')
    resetDialog.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function onToggle(account) {
  const nextStatus = account.status === 1 ? 0 : 1
  await ElMessageBox.confirm(
    `确认${nextStatus === 1 ? '启用' : '禁用'}账号【${account.username}】？${nextStatus === 0 ? '禁用后该账号现有会话将在下一次请求时失效。' : ''}`,
    nextStatus === 1 ? '启用账号' : '禁用账号',
    { type: 'warning' }
  )
  actionKey.value = `account-toggle-${account.id}`
  try {
    await toggleAccountStatus(account.id, nextStatus)
    ElMessage.success(nextStatus === 1 ? '账号已启用' : '账号已禁用')
    await load()
  } finally {
    actionKey.value = ''
  }
}

async function onDeleteAccount(account) {
  await ElMessageBox.confirm(
    `确认删除登录账号【${account.username}】？仅删除登录能力，成员档案和流水都会保留。`,
    '删除账号',
    { type: 'warning' }
  )
  actionKey.value = `account-delete-${account.id}`
  try {
    await deleteAccount(account.id)
    ElMessage.success('登录账号已删除，成员档案仍保留')
    await load()
  } finally {
    actionKey.value = ''
  }
}
</script>

<style scoped>
.bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}
</style>
