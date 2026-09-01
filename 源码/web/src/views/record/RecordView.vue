<template>
  <div>
    <!-- 查询条件 -->
    <div class="page-card">
      <h3 class="card-title">查询条件</h3>
      <el-form :model="query" label-width="72px" class="query-bar">
        <el-row :gutter="14">
          <el-col :md="6" :sm="12">
            <el-form-item label="类型">
              <el-select v-model="query.type" placeholder="全部" clearable style="width: 100%">
                <el-option label="收入" :value="1" />
                <el-option label="支出" :value="2" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :md="6" :sm="12">
            <el-form-item label="成员">
              <el-select v-model="query.memberId" placeholder="全部" clearable style="width: 100%">
                <el-option v-for="m in members" :key="m.id" :label="m.memberName" :value="m.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :md="6" :sm="12">
            <el-form-item label="分类">
              <el-cascader
                v-model="queryCategory"
                :options="categoryOptions"
                :props="{ checkStrictly: true, value: 'id', label: 'categoryName', children: 'children' }"
                placeholder="全部（选一级含其下全部）"
                clearable
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :md="6" :sm="12">
            <el-form-item label="日期">
              <el-date-picker
                v-model="dateRange"
                type="daterange"
                start-placeholder="开始"
                end-placeholder="结束"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :md="6" :sm="12">
            <el-form-item label="关键字">
              <el-input v-model="query.keyword" placeholder="商家或备注" clearable />
            </el-form-item>
          </el-col>
          <el-col :md="6" :sm="12">
            <el-form-item label="片区">
              <el-select v-model="query.area" placeholder="全部" clearable filterable style="width: 100%">
                <el-option v-for="a in options.areas" :key="a" :label="a" :value="a" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :md="6" :sm="12">
            <el-form-item label="支付方式">
              <el-select v-model="query.payMethod" placeholder="全部" clearable style="width: 100%">
                <el-option v-for="p in options.payMethods" :key="p" :label="p" :value="p" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :md="6" :sm="12">
            <el-form-item label="人情往来">
              <el-select v-model="query.isGift" placeholder="不限" clearable style="width: 100%">
                <el-option label="是" :value="1" />
                <el-option label="否" :value="0" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <div class="btns">
          <el-button type="primary" plain :icon="Search" @click="search">查询</el-button>
          <el-button :icon="RefreshLeft" @click="reset">重置</el-button>
          <el-button type="primary" :icon="Plus" @click="openAdd">录入流水</el-button>
        </div>
      </el-form>
    </div>

    <!-- 结果汇总 + 列表 -->
    <div class="page-card">
      <div class="summary">
        <span>共 <b>{{ total }}</b> 笔</span>
        <span>收入合计 <b class="amount-income">¥{{ money(sumIncome) }}</b></span>
        <span>支出合计 <b class="amount-expense">¥{{ money(sumExpense) }}</b></span>
        <span>
          结余
          <b :class="sumIncome - sumExpense >= 0 ? 'amount-income' : 'amount-expense'">
            ¥{{ money(sumIncome - sumExpense) }}
          </b>
        </span>
      </div>

      <el-table :data="list" v-loading="loading" border stripe size="small" empty-text="没有符合条件的流水">
        <el-table-column prop="recordDate" label="日期" width="102" />
        <el-table-column label="类型" width="66" align="center">
          <template #default="{ row }">
            <el-tag :type="row.type === 1 ? 'success' : 'danger'" size="small" effect="light">
              {{ typeText(row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="memberName" label="成员" width="88" />
        <el-table-column label="分类" min-width="140">
          <template #default="{ row }">
            <span v-if="row.parentCategoryName" class="text-light">{{ row.parentCategoryName }} / </span>
            {{ row.categoryName }}
          </template>
        </el-table-column>
        <el-table-column label="金额" width="118" align="right">
          <template #default="{ row }">
            <span :class="row.type === 1 ? 'amount-income' : 'amount-expense'">
              {{ row.type === 1 ? '+' : '-' }}{{ money(row.amount) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="merchant" label="商家" min-width="110" show-overflow-tooltip />
        <el-table-column prop="area" label="片区" width="82" />
        <el-table-column prop="payMethod" label="支付方式" width="86" />
        <el-table-column label="人情" width="60" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isGift === 1" type="warning" size="small" effect="plain">是</el-tag>
            <span v-else class="text-light">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="110" show-overflow-tooltip />
        <el-table-column label="操作" width="118" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pager"
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        @current-change="load"
        @size-change="search"
      />
    </div>

    <!-- 录入 / 编辑 -->
    <el-dialog v-model="dialog" :title="form.id ? '编辑流水' : '录入流水'" width="620px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <el-form-item label="收支类型" prop="type">
          <el-radio-group v-model="form.type" @change="onTypeChange">
            <el-radio-button :value="2">支出</el-radio-button>
            <el-radio-button :value="1">收入</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="家庭成员" prop="memberId">
              <el-select v-model="form.memberId" placeholder="请选择" style="width: 100%">
                <el-option
                  v-for="m in members"
                  :key="m.id"
                  :label="m.relation ? `${m.memberName}（${m.relation}）` : m.memberName"
                  :value="m.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="金额" prop="amount">
              <el-input-number
                v-model="form.amount"
                :min="0.01"
                :precision="2"
                :step="10"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="收支分类" prop="categoryPath">
          <el-cascader
            v-model="form.categoryPath"
            :options="formCategoryOptions"
            :props="{ checkStrictly: true, value: 'id', label: 'categoryName', children: 'children' }"
            placeholder="请选择分类，可只选一级"
            style="width: 100%"
          />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="发生日期" prop="recordDate">
              <el-date-picker
                v-model="form.recordDate"
                type="date"
                value-format="YYYY-MM-DD"
                :disabled-date="(d) => d > new Date()"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="支付方式">
              <el-select v-model="form.payMethod" placeholder="可不填" clearable style="width: 100%">
                <el-option v-for="p in options.payMethods" :key="p" :label="p" :value="p" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="商家">
              <el-select
                v-model="form.merchant"
                placeholder="如：海底捞（可新建）"
                filterable
                allow-create
                default-first-option
                clearable
                style="width: 100%"
              >
                <el-option v-for="m in options.merchants" :key="m" :label="m" :value="m" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="消费片区">
              <el-select
                v-model="form.area"
                placeholder="如：城东（可新建）"
                filterable
                allow-create
                default-first-option
                clearable
                style="width: 100%"
              >
                <el-option v-for="a in options.areas" :key="a" :label="a" :value="a" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="人情往来">
          <el-switch v-model="form.isGift" :active-value="1" :inactive-value="0" />
          <span class="text-light" style="margin-left: 10px">
            礼金、送礼、请客等往来支出勾选后可做专项分析
          </span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="255" show-word-limit />
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
import { Plus, RefreshLeft, Search } from '@element-plus/icons-vue'
import { addRecord, deleteRecord, pageRecord, recordOptions, updateRecord } from '../../api/record'
import { listMember } from '../../api/member'
import { treeCategory } from '../../api/category'
import { money, today, typeText } from '../../utils/format'

const loading = ref(false)
const saving = ref(false)
const list = ref([])
const total = ref(0)
const sumIncome = ref(0)
const sumExpense = ref(0)

const members = ref([])
const categoryTree = ref([])
const options = ref({ merchants: [], areas: [], payMethods: [] })

const query = ref(emptyQuery())
const dateRange = ref([])
// 级联组件返回的是路径数组，查询时只取最后一级
const queryCategory = ref([])

function emptyQuery() {
  return {
    type: null,
    memberId: null,
    categoryId: null,
    startDate: null,
    endDate: null,
    keyword: '',
    payMethod: null,
    area: null,
    isGift: null,
    pageNum: 1,
    pageSize: 10
  }
}

/** 查询用的分类候选：收入支出都列出来，按类型分成两组 */
const categoryOptions = computed(() => categoryTree.value)

/** 录入表单的分类候选：只显示与当前收支类型匹配的分类，避免选错类型 */
const formCategoryOptions = computed(() =>
  categoryTree.value.filter((c) => c.type === form.value.type)
)

const dialog = ref(false)
const formRef = ref()
const form = ref(emptyForm())

function emptyForm() {
  return {
    id: null,
    type: 2,
    memberId: null,
    categoryPath: [],
    amount: null,
    recordDate: today(),
    merchant: null,
    area: null,
    payMethod: null,
    isGift: 0,
    remark: ''
  }
}

const rules = {
  type: [{ required: true, message: '请选择收支类型', trigger: 'change' }],
  memberId: [{ required: true, message: '请选择家庭成员', trigger: 'change' }],
  amount: [{ required: true, message: '请输入金额', trigger: 'blur' }],
  categoryPath: [{ required: true, message: '请选择收支分类', trigger: 'change' }],
  recordDate: [{ required: true, message: '请选择发生日期', trigger: 'change' }]
}

onMounted(async () => {
  members.value = await listMember()
  categoryTree.value = await treeCategory()
  options.value = await recordOptions()
  await load()
})

async function load() {
  loading.value = true
  try {
    query.value.startDate = dateRange.value?.[0] || null
    query.value.endDate = dateRange.value?.[1] || null
    query.value.categoryId = queryCategory.value?.length
      ? queryCategory.value[queryCategory.value.length - 1]
      : null

    const res = await pageRecord(query.value)
    list.value = res.page.list
    total.value = res.page.total
    sumIncome.value = res.sumIncome
    sumExpense.value = res.sumExpense
  } finally {
    loading.value = false
  }
}

function search() {
  query.value.pageNum = 1
  load()
}

function reset() {
  const size = query.value.pageSize
  query.value = emptyQuery()
  query.value.pageSize = size
  dateRange.value = []
  queryCategory.value = []
  load()
}

function openAdd() {
  form.value = emptyForm()
  dialog.value = true
}

function openEdit(row) {
  form.value = {
    id: row.id,
    type: row.type,
    memberId: row.memberId,
    // 编辑时把分类还原成级联需要的路径：有父分类就是两级，否则一级
    categoryPath: row.parentCategoryName ? [findParentId(row.categoryId), row.categoryId] : [row.categoryId],
    amount: Number(row.amount),
    recordDate: row.recordDate,
    merchant: row.merchant,
    area: row.area,
    payMethod: row.payMethod,
    isGift: row.isGift,
    remark: row.remark
  }
  dialog.value = true
}

/** 从已加载的分类树里反查某个二级分类的父分类ID，避免为此再发一次请求 */
function findParentId(categoryId) {
  for (const parent of categoryTree.value) {
    if ((parent.children || []).some((c) => c.id === categoryId)) {
      return parent.id
    }
  }
  return null
}

function onTypeChange() {
  // 收支类型一变，原来选的分类必然不匹配，直接清空让用户重选
  form.value.categoryPath = []
}

async function onSave() {
  await formRef.value.validate()
  const path = form.value.categoryPath
  const payload = {
    ...form.value,
    categoryId: path[path.length - 1]
  }
  delete payload.categoryPath

  saving.value = true
  try {
    if (form.value.id) {
      await updateRecord(form.value.id, payload)
      ElMessage.success('修改成功')
    } else {
      await addRecord(payload)
      ElMessage.success('录入成功')
    }
    dialog.value = false
    // 新录入的商家/片区要进候选列表，所以重新拉一次选项
    options.value = await recordOptions()
    await load()
  } finally {
    saving.value = false
  }
}

async function onDelete(row) {
  await ElMessageBox.confirm(
    `确认删除 ${row.recordDate} 的这笔${typeText(row.type)}（¥${money(row.amount)}）？`,
    '删除确认',
    { type: 'warning' }
  )
  await deleteRecord(row.id)
  ElMessage.success('删除成功')
  await load()
}
</script>

<style scoped>
.btns {
  padding-left: 72px;
}

.summary {
  display: flex;
  gap: 26px;
  flex-wrap: wrap;
  padding: 10px 14px;
  margin-bottom: 12px;
  background: #f6f8f7;
  border-radius: 4px;
  font-size: 13px;
  color: var(--gjp-text-light);
}

.summary b {
  color: var(--gjp-text);
  font-size: 15px;
  margin-left: 4px;
}

.pager {
  margin-top: 14px;
  justify-content: flex-end;
}
</style>
