<template>
  <div>
    <div class="page-card">
      <h3 class="card-title">收支分类管理</h3>
      <div class="bar">
        <div>
          <el-radio-group v-model="type" size="small" @change="load">
            <el-radio-button :value="2">支出分类</el-radio-button>
            <el-radio-button :value="1">收入分类</el-radio-button>
          </el-radio-group>
          <span class="text-light" style="margin-left: 12px">
            分类固定两级；带"预置"标记的是系统初始化分类，可改名不可删除
          </span>
        </div>
        <div>
          <el-button type="primary" :icon="Plus" @click="openAdd(null)">新增一级分类</el-button>
        </div>
      </div>

      <el-table
        :data="tree"
        v-loading="loading"
        row-key="id"
        border
        size="small"
        :tree-props="{ children: 'children' }"
        default-expand-all
      >
        <el-table-column prop="categoryName" label="分类名称" min-width="220" />
        <el-table-column label="层级" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.parentId === 0 ? 'primary' : 'info'" size="small" effect="plain">
              {{ row.parentId === 0 ? '一级' : '二级' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isDefault === 1" type="warning" size="small" effect="plain">预置</el-tag>
            <el-tag v-else type="success" size="small" effect="plain">自定义</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sortNo" label="排序号" width="80" align="center" />
        <el-table-column label="操作" width="230" align="center">
          <template #default="{ row }">
            <el-button v-if="row.parentId === 0" link type="success" size="small" @click="openAdd(row)">
              添加子分类
            </el-button>
            <el-button link type="primary" size="small" @click="openEdit(row)">改名</el-button>
            <el-button
              link
              type="danger"
              size="small"
              :disabled="row.isDefault === 1"
              @click="onDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog" :title="dialogTitle" width="430px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <el-form-item v-if="form.parentName" label="父分类">
          <el-input :model-value="form.parentName" disabled />
        </el-form-item>
        <el-form-item label="分类名称" prop="categoryName">
          <el-input v-model="form.categoryName" placeholder="如：外出就餐" maxlength="20" />
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="form.sortNo" :min="0" :max="999" controls-position="right" />
          <div class="text-light">数字越小越靠前</div>
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
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { addCategory, deleteCategory, treeCategory, updateCategory } from '../../api/category'

const loading = ref(false)
const saving = ref(false)
const type = ref(2)
const tree = ref([])

const dialog = ref(false)
const formRef = ref()
const form = ref(emptyForm())
const editing = ref(false)

const rules = {
  categoryName: [{ required: true, message: '请输入分类名称', trigger: 'blur' }]
}

const dialogTitle = computed(() => {
  if (editing.value) return '修改分类名称'
  return form.value.parentId ? '新增子分类' : '新增一级分类'
})

function emptyForm() {
  return { id: null, categoryName: '', parentId: 0, parentName: '', sortNo: 99, type: 2 }
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    tree.value = await treeCategory(type.value)
  } finally {
    loading.value = false
  }
}

function openAdd(parent) {
  editing.value = false
  form.value = emptyForm()
  form.value.type = type.value
  if (parent) {
    form.value.parentId = parent.id
    form.value.parentName = parent.categoryName
  }
  dialog.value = true
}

function openEdit(row) {
  editing.value = true
  form.value = {
    id: row.id,
    categoryName: row.categoryName,
    parentId: row.parentId,
    parentName: '',
    sortNo: row.sortNo,
    type: row.type
  }
  dialog.value = true
}

async function onSave() {
  await formRef.value.validate()
  saving.value = true
  try {
    if (editing.value) {
      await updateCategory(form.value.id, form.value)
      ElMessage.success('修改成功')
    } else {
      await addCategory(form.value)
      ElMessage.success('新增成功')
    }
    dialog.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function onDelete(row) {
  await ElMessageBox.confirm(`确认删除分类【${row.categoryName}】？`, '删除确认', { type: 'warning' })
  await deleteCategory(row.id)
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
  flex-wrap: wrap;
}
</style>
