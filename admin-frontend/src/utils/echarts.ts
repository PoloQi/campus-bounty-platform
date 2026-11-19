/**
 * ECharts 按需注册。
 * 全量 import * as echarts from 'echarts' 会把约 1MB 塞进首屏 chunk，
 * 本工程只需要折线 + 饼图。
 */
import * as echarts from 'echarts/core'
import { LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { EChartsCoreOption } from 'echarts/core'

echarts.use([LineChart, PieChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

export { echarts }
export type { EChartsCoreOption }

/** 与设计系统一致的图表配色（六态顺序固定） */
export const CHART_COLORS = ['#64748B', '#2563EB', '#7EA2F6', '#DC2626', '#16A34A', '#94A3B8']

export const AXIS_LABEL_COLOR = '#8A94A6'
export const AXIS_LINE_COLOR = '#E6EAF2'
export const SPLIT_LINE_COLOR = '#F0F3F8'
