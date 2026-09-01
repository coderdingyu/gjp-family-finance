<template>
  <div>
    <div class="page-card">
      <h3 class="card-title">家庭成员管理</h3>
      <div class="bar">
        <span class="text-light">
          成员是收支数据的归属单位。设置月度预算后，看板与智能分析会自动做超支预警。
        </span>
        <el-button type="primary" :icon="Plus" @click="openAdd">新增成员</el-button>
      </div>

      <el-table :data="list" v-loading="loading" border stripe size="small">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="memberName" label="姓名" min-width="120" />
        <el-table-column label="家庭关系" width="110">
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
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="130" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog" :title="form.id ? '编辑成员' : '新增成员'" width="440px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <el-form-item label="姓名" prop="memberName">
          <el-input v-model="form.memberName" placeholder="请输入成员姓名" maxlength="20" />
        </el-form-item>
        <el-form-item label="家庭关系" prop="relation">
          <el-select v-model="form.relation" placeholder="请选择" style="width: 100%">
            <el-option v-for="r in relations" :key="r" :label="r" :value="r" />
          </el-select>
        </el-form-item>
        <el-form-item label="月度预算">
          <el-input-number
            v-model="form.monthlyBudget"
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
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { addMember, deleteMember, listMember, updateMember } from '../../api/member'
import { money } from '../../utils/format'

const relations = ['本人', '配偶', '子女', '父母', '其他']
const loading = ref(false)
const saving = ref(false)
const list = ref([])
const dialog = ref(false)
const formRef = ref()
const form = ref(emptyForm())

const rules = {
  memberName: [{ required: true, message: '请输入成员姓名', trigger: 'blur' }],
  relation: [{ required: true, message: '请选择家庭关系', trigger: 'change' }]
}

function emptyForm() {
  return { id: null, memberName: '', relation: '', monthlyBudget: 0 }
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    list.value = await listMember()
  } finally {
    loading.value = false
  }
}

function openAdd() {
  form.value = emptyForm()
  dialog.value = true
}

function openEdit(row) {
  form.value = {
    id: row.id,
    memberName: row.memberName,
    relation: row.relation,
    monthlyBudget: Number(row.monthlyBudget || 0)
  }
  dialog.value = true
}

async function onSave() {
  await formRef.value.validate()
  saving.value = true
  try {
    if (form.value.id) {
      await updateMember(form.value.id, form.value)
      ElMessage.success('修改成功')
    } else {
      await addMember(form.value)
      ElMessage.success('新增成功')
    }
    dialog.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function onDelete(row) {
  await ElMessageBox.confirm(`确认删除成员【${row.memberName}】？`, '删除确认', { type: 'warning' })
  await deleteMember(row.id)
  ElMessage.success('删除成功')
  await load()
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
