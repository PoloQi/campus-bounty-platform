<script setup lang="ts">
import { ref, watch } from 'vue'
import { useEChart } from '@/composables/useEChart'
import { AXIS_LABEL_COLOR, AXIS_LINE_COLOR, CHART_COLORS, SPLIT_LINE_COLOR, echarts } from '@/utils/echarts'
import { formatMonthDay } from '@/utils/datetime'
import type { DayCount } from '@/types/domain'

const props = defineProps<{ points: DayCount[] }>()

const container = ref<HTMLElement | null>(null)

const { render } = useEChart(container, () => {
  const labels = props.points.map((item) => formatMonthDay(item.orderDay))
  const values = props.points.map((item) => item.total)

  return {
    // 左右留够半个 symbol 的宽度：首尾点正好落在绘图区边界上，余量不足会被裁成半圆
    grid: { left: 10, right: 22, top: 18, bottom: 2, containLabel: true },
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#0f172a',
      borderWidth: 0,
      padding: [7, 11],
      textStyle: { color: '#fff', fontSize: 12 },
      axisPointer: { type: 'line', lineStyle: { color: '#cbd5e1', type: 'dashed' } },
      formatter: (params: unknown) => {
        const list = params as Array<{ axisValue: string; data: number }>
        const row = list[0]
        return `${row.axisValue}<br/>成交 ${row.data} 单`
      },
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: labels,
      axisTick: { show: false },
      axisLine: { lineStyle: { color: AXIS_LINE_COLOR } },
      axisLabel: { color: AXIS_LABEL_COLOR, fontSize: 11, margin: 10 },
    },
    yAxis: {
      type: 'value',
      // 订单数是整数，不加这个会出现 0.5 这类刻度
      minInterval: 1,
      // 峰值默认会正好顶到绘图区上边缘，留 20% 余量才不显得被裁
      max: (value: { max: number }) => {
        const peak = Math.max(1, value.max)
        return peak <= 4 ? peak + 1 : Math.ceil(peak * 1.2)
      },
      splitNumber: 4,
      splitLine: { lineStyle: { color: SPLIT_LINE_COLOR } },
      axisLabel: { color: AXIS_LABEL_COLOR, fontSize: 11 },
    },
    series: [
      {
        type: 'line',
        data: values,
        smooth: true,
        symbol: 'circle',
        symbolSize: 7,
        showSymbol: true,
        // boundaryGap:false 让首尾点正好落在绘图区边缘，不关 clip 会被裁成半圆
        clip: false,
        itemStyle: { color: CHART_COLORS[1], borderColor: '#fff', borderWidth: 2 },
        lineStyle: { width: 2.4, color: CHART_COLORS[1] },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(37, 99, 235, 0.22)' },
            { offset: 1, color: 'rgba(37, 99, 235, 0.01)' },
          ]),
        },
        emphasis: { scale: 1.25 },
      },
    ],
  }
})

watch(() => props.points, render, { deep: true })
</script>

<template>
  <div ref="container" class="chart-canvas" />
</template>
