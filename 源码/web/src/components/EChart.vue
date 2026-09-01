<template>
  <div ref="el" :style="{ width: '100%', height: height }"></div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

/**
 * ECharts 通用包装组件。
 * 统计页有十几张图，如果每个页面都自己 init/dispose/监听 resize，重复代码会非常多，
 * 所以统一封装成一个组件：外部只负责传 option。
 */
const props = defineProps({
  option: { type: Object, required: true },
  height: { type: String, default: '320px' },
  // 数据为空时显示的文案，避免出现一张什么都没有的空白图
  emptyText: { type: String, default: '暂无数据' },
  empty: { type: Boolean, default: false }
})

const el = ref()
let chart = null

function render() {
  if (!el.value) return
  if (!chart) {
    chart = echarts.init(el.value)
  }
  if (props.empty) {
    chart.clear()
    chart.setOption({
      title: {
        text: props.emptyText,
        left: 'center',
        top: 'middle',
        textStyle: { color: '#909399', fontSize: 13, fontWeight: 'normal' }
      }
    })
    return
  }
  // notMerge = true：切换查询条件后旧的系列不会残留
  chart.setOption(props.option, true)
}

function onResize() {
  chart && chart.resize()
}

onMounted(async () => {
  await nextTick()
  render()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chart && chart.dispose()
  chart = null
})

watch(() => [props.option, props.empty], render, { deep: true })

// 侧边栏或容器尺寸变化时外部可以主动调用
defineExpose({ resize: onResize })
</script>
