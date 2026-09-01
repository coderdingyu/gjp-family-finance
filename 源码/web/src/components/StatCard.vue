<template>
  <div class="stat-card" :style="{ borderTopColor: color }">
    <div class="label">{{ label }}</div>
    <div class="value" :style="{ color: color }">
      <span class="unit" v-if="prefix">{{ prefix }}</span>{{ display }}
    </div>
    <div class="sub" v-if="sub">{{ sub }}</div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { money } from '../utils/format'

/** 看板顶部的指标卡。数字类型自动做千分位格式化。 */
const props = defineProps({
  label: String,
  value: [Number, String],
  sub: String,
  color: { type: String, default: '#2e7d5b' },
  prefix: { type: String, default: '¥' },
  // 笔数这类整数不需要两位小数
  raw: { type: Boolean, default: false }
})

const display = computed(() => (props.raw ? props.value : money(props.value)))
</script>

<style scoped>
.stat-card {
  background: #fff;
  border-radius: 6px;
  border-top: 3px solid;
  padding: 16px 18px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

.label {
  font-size: 13px;
  color: var(--gjp-text-light);
}

.value {
  font-size: 24px;
  font-weight: 600;
  margin-top: 8px;
  line-height: 1.2;
}

.unit {
  font-size: 14px;
  margin-right: 2px;
}

.sub {
  font-size: 12px;
  color: var(--gjp-text-light);
  margin-top: 6px;
}
</style>
