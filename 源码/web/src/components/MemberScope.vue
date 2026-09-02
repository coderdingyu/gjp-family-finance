<template>
  <div v-if="!scopeLocked" class="scope">
    <span class="text-light">查看范围</span>
    <el-select
      :model-value="modelValue"
      placeholder="全家汇总"
      clearable
      size="small"
      style="width: 150px"
      @update:model-value="(v) => emit('update:modelValue', v)"
      @change="() => emit('change')"
    >
      <el-option
        v-for="m in members"
        :key="m.id"
        :label="m.memberName + (m.relation ? `（${m.relation}）` : '')"
        :value="m.id"
      />
    </el-select>
  </div>
  <el-tag v-else type="info" size="small" effect="plain" class="locked">
    <el-icon><Lock /></el-icon>
    仅我的数据
  </el-tag>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { listMember } from '../api/member'
import { scopeLocked } from '../utils/auth'

/**
 * 成员范围选择器（需求第 9 条的界面部分）。
 *
 * 户主看到一个下拉框：不选=全家汇总，选中某人=只看那个人的账。
 * 普通成员看到的是一个锁定标签 —— 与其给一个点了没反应的下拉框，
 * 不如明确告诉用户"你只能看自己"，减少困惑。
 *
 * 成员列表在组件内部加载：这个选择器会出现在看板、统计、分析三个页面，
 * 每个页面各写一遍加载逻辑是重复的。
 */
defineProps({
  modelValue: { type: [Number, String, null], default: null }
})
const emit = defineEmits(['update:modelValue', 'change'])

const members = ref([])

onMounted(async () => {
  if (!scopeLocked.value) {
    members.value = await listMember()
  }
})
</script>

<style scoped>
.scope {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.locked {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
</style>
