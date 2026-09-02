<template>
  <div>
    <div class="page-card">
      <h3 class="card-title">
        操作日志
        <span class="text-light">{{ scopeHint }}</span>
      </h3>
      <el-form :inline="true" class="query-bar">
        <el-form-item label="模块">
          <el-select v-model="query.module" placeholder="全部" clearable style="width: 120px" @change="search">
            <el-option v-for="m in options.modules" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="动作">
          <el-select v-model="query.action" placeholder="全部" clearable style="width: 130px" @change="search">
            <el-option v-for="a in options.actions" :key="a" :label="a" :value="a" />
          </el-select>
        </el-form-item>
        <el-form-item label="结果">
          <el-select v-model="query.success" placeholder="全部" clearable style="width: 100px" @change="search">
            <el-option label="成功" :value="1" />
            <el-option label="失败" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间">
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 340px"
            @change="search"
          />
        </el-form-item>
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="摘要 / 账号 / 姓名" clearable
                    style="width: 180px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain :icon="Search" @click="search">查询</el-button>
          <el-button :icon="RefreshLeft" @click="reset">重置</el-button>
        </el-form-item>
      </el-form>

      <div v-if="moduleStat.length" class="stat-bar">
        <span class="text-light">按模块：</span>
        <el-tag v-for="m in moduleStat" :key="m.name" size="small" effect="plain"
                class="stat-tag" @click="quickFilter(m.name)">
          {{ m.name }} {{ m.value }}
        </el-tag>
      </div>

      <el-table :data="list" v-loading="loading" border stripe size="small"
                empty-text="没有符合条件的日志">
        <el-table-column prop="createTime" label="时间" width="160" />
        <el-table-column label="结果" width="66" align="center">
          <template #default="{ row }">
            <el-tag :type="row.success === 1 ? 'success' : 'danger'" size="small" effect="light">
              {{ row.success === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="模块" width="76" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.module }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="action" label="动作" width="86" align="center" />
        <el-table-column label="操作人" width="130">
          <template #default="{ row }">
            {{ row.realName || '—' }}
            <span class="text-light">{{ row.username ? '(' + row.username + ')' : '' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="summary" label="操作摘要" min-width="300" show-overflow-tooltip />
        <el-table-column prop="ip" label="来源IP" width="120" />
        <el-table-column label="失败原因" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.errorMsg" class="amount-expense">{{ row.errorMsg }}</span>
            <span v-else class="text-light">—</span>
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
        :page-sizes="[20, 50, 100]"
        @current-change="load"
        @size-change="search"
      />

      <p class="text-light note">
        说明：只记录写操作（新增 / 修改 / 删除 / 导入 / 登录），不记录查询。
        查询量是写操作的几十倍，全记会让日志表迅速变成库里最大的表，而对排查问题几乎没有帮助。
      </p>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { RefreshLeft, Search } from '@element-plus/icons-vue'
import { logModuleStat, logOptions, pageLog } from '../../api/log'
import { isAdmin, isOwner, scopeLocked } from '../../utils/auth'

/**
 * 操作日志页（需求第 7 条）。
 * 可见范围由后端按角色决定：普通成员只看自己、户主看全家、管理员看全部。
 * 这里只负责展示与筛选，不做任何范围判断，避免前后端两套逻辑对不上。
 */
const loading = ref(false)
const list = ref([])
const total = ref(0)
const options = ref({ modules: [], actions: [] })
const moduleStat = ref([])
const timeRange = ref([])
const query = ref(emptyQuery())

const scopeHint = computed(() => {
  if (isAdmin.value) return '（系统管理员视角：全部家庭）'
  if (isOwner.value) return '（户主视角：本家庭全部成员）'
  return '（仅显示我自己的操作）'
})

function emptyQuery() {
  return {
    module: null,
    action: null,
    success: null,
    keyword: '',
    startTime: null,
    endTime: null,
    pageNum: 1,
    pageSize: 20
  }
}

onMounted(async () => {
  options.value = await logOptions()
  moduleStat.value = await logModuleStat()
  await load()
})

async function load() {
  loading.value = true
  try {
    query.value.startTime = timeRange.value?.[0] || null
    query.value.endTime = timeRange.value?.[1] || null
    const res = await pageLog(query.value)
    list.value = res.list
    total.value = res.total
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
  timeRange.value = []
  load()
}

function quickFilter(module) {
  query.value.module = query.value.module === module ? null : module
  search()
}
</script>

<style scoped>
.card-title .text-light {
  font-weight: normal;
  margin-left: 6px;
}

.stat-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.stat-tag {
  cursor: pointer;
}

.pager {
  margin-top: 14px;
  justify-content: flex-end;
}

.note {
  line-height: 1.8;
  margin: 12px 0 0;
}
</style>
