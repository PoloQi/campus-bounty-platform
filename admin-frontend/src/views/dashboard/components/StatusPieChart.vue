<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useEChart } from '@/composables/useEChart'
import { AXIS_LINE_COLOR, CHART_COLORS } from '@/utils/echarts'
import { dictLabel, orderStatusSelect } from '@/constants/options'
import { formatNumber } from '@/utils/money'
import type { StatusCount } from '@/types/domain'

const props = defineProps<{ items: StatusCount[] }>()

const container = ref<HTMLElement | null>(null)

/** 颜色必须绑定「枚举位置」而不是「数组下标」，否则筛掉 0 值后颜色会整体错位 */
const colorOf = (status: string): string => {
  const index = orderStatusSelect.findIndex((option) => option.value === status)
  return CHART_COLORS[index >= 0 ? index : 0]
}

const total = computed(() => props.items.reduce((sum, item) => sum + item.total, 0))

const legend = computed(() =>
  props.items.map((item) => ({
    key: item.status,
    label: dictLabel('orderStatus', item.status),
    total: item.total,
    color: colorOf(item.status),
    percent: total.value > 0 ? Math.round((item.total / total.value) * 100) : 0,
  })),
)

const { render } = useEChart(container, () => ({
  tooltip: {
    trigger: 'item',
    backgroundColor: '#0f172a',
    borderWidth: 0,
    padding: [7, 11],
    textStyle: { color: '#fff', fontSize: 12 },
    formatter: (params: unknown) => {
      const row = params as { name: string; value: number; percent: number }
      return `${row.name}<br/>${row.value} 单 · ${row.percent}%`
    },
  },
  series: [
    {
      type: 'pie',
      radius: ['62%', '86%'],
      center: ['50%', '50%'],
      avoidLabelOverlap: true,
      // 标签一律关掉：六个扇区放外部标签会互相压，右侧已有自绘图例承担说明
      label: { show: false },
      labelLine: { show: false },
      itemStyle: { borderColor: '#fff', borderWidth: 2 },
      data: props.items.map((item) => ({
        name: dictLabel('orderStatus', item.status),
        value: item.total,
        itemStyle: { color: colorOf(item.status) },
      })),
      emphasis: { scale: true, scaleSize: 4, itemStyle: { borderWidth: 2 } },
    },
  ],
  // 留一个不参与绘制的干净轴线色，避免某些主题下出现默认灰边
  color: [AXIS_LINE_COLOR],
}))

watch(() => props.items, render, { deep: true })
</script>

<template>
  <div class="pie-layout">
    <div class="pie-canvas-wrap">
      <div ref="container" class="pie-canvas" />
      <div class="pie-center">
        <span class="pie-center__num">{{ formatNumber(total) }}</span>
        <span class="pie-center__label">订单总量</span>
      </div>
    </div>

    <ul class="pie-legend">
      <li v-for="item in legend" :key="item.key" class="pie-legend__item">
        <span class="pie-legend__dot" :style="{ background: item.color }" />
        <span class="pie-legend__label">{{ item.label }}</span>
        <span class="pie-legend__value">{{ formatNumber(item.total) }}</span>
        <span class="pie-legend__percent">{{ item.percent }}%</span>
      </li>
    </ul>
  </div>
</template>
