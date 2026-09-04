<template>
  <div class="stat-card" :style="{ '--badge': color }">
    <div class="stat-head">
      <span class="stat-badge" :class="{ 'is-empty': !icon }">{{ icon }}</span>
      <div class="label">{{ label }}</div>
    </div>
    <div class="value">
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
  color: { type: String, default: 'var(--gjp-primary)' },
  icon: { type: String, default: '' },
  prefix: { type: String, default: '¥' },
  // 笔数这类整数不需要两位小数
  raw: { type: Boolean, default: false }
})

const display = computed(() => (props.raw ? props.value : money(props.value)))
</script>

<style scoped>
.stat-card {
  background: var(--gjp-card);
  border-radius: var(--gjp-radius);
  padding: 20px 22px;
  box-shadow: var(--gjp-shadow);
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--gjp-shadow-hover);
}

.stat-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.stat-badge {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  font-size: 14px;
  font-weight: 600;
  line-height: 1;
  color: var(--badge);
  background: color-mix(in srgb, var(--badge) 12%, #fff);
}

.stat-badge.is-empty::before {
  content: "";
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--badge);
}

.label {
  font-size: 13px;
  color: #64748b;
}

.value {
  font-size: 24px;
  font-weight: 600;
  margin-top: 12px;
  line-height: 1.2;
  color: var(--gjp-text);
}

.unit {
  font-size: 14px;
  margin-right: 2px;
  font-weight: 600;
}

.sub {
  font-size: 12px;
  color: var(--gjp-text-light);
  margin-top: 6px;
}
</style>
