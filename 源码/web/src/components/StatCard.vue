<template>
  <div class="stat-card">
    <div class="head">
      <span class="icon-badge" :style="badgeStyle">{{ icon || '●' }}</span>
      <span class="label">{{ label }}</span>
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

/** 看板顶部的指标卡。Soft UI：圆形色调图标徽章 + 柔和阴影卡片（跟随 --gjp-card）。 */
const props = defineProps({
  label: String,
  value: [Number, String],
  sub: String,
  color: { type: String, default: '#4f46e5' },
  icon: { type: String, default: '' },
  prefix: { type: String, default: '¥' },
  // 笔数这类整数不需要两位小数
  raw: { type: Boolean, default: false }
})

const display = computed(() => (props.raw ? props.value : money(props.value)))

const badgeStyle = computed(() => ({
  background: hexToRgba(props.color, 0.12),
  color: props.color
}))

function hexToRgba(hex, alpha) {
  const h = String(hex || '#4f46e5').replace('#', '')
  const full = h.length === 3 ? h.split('').map((c) => c + c).join('') : h
  const n = parseInt(full, 16)
  if (Number.isNaN(n)) return `rgba(79, 70, 229, ${alpha})`
  const r = (n >> 16) & 255
  const g = (n >> 8) & 255
  const b = n & 255
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}
</script>

<style scoped>
.stat-card {
  background: var(--gjp-card, var(--gjp-surface, #fff));
  border-radius: var(--gjp-radius, 20px);
  padding: 20px 22px;
  box-shadow: var(--gjp-soft-shadow, 0 10px 40px -10px rgba(148, 163, 184, 0.35));
  border: none;
  color: var(--gjp-text);
}

.head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.icon-badge {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  flex-shrink: 0;
}

.label {
  font-size: 12px;
  color: var(--gjp-text-muted, var(--gjp-text-light));
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
}

.sub {
  font-size: 12px;
  color: var(--gjp-text-light);
  margin-top: 6px;
}
</style>
