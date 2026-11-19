import { onBeforeUnmount, onMounted, type Ref } from 'vue'
import { echarts, type EChartsCoreOption } from '@/utils/echarts'
import type { ECharts } from 'echarts/core'

/**
 * 图表生命周期封装。三个必须处理的点：
 *   1. 容器必须有显式高度，否则 clientHeight=0，图表「空白但不报错」
 *   2. 容器隐藏时不 init（调用方用 v-if 包住）
 *   3. 卸载时 disconnect + dispose，否则反复进出会泄漏 canvas
 */
export function useEChart(
  container: Ref<HTMLElement | null | undefined>,
  build: () => EChartsCoreOption,
) {
  let chart: ECharts | null = null
  let observer: ResizeObserver | null = null

  function render(): void {
    const el = container.value
    if (!el) return

    if (!chart) {
      chart = echarts.init(el)
    }
    chart.setOption(build(), true)
  }

  function resize(): void {
    chart?.resize()
  }

  onMounted(() => {
    render()
    const el = container.value
    if (!el) return
    // 监听容器而非 window：侧栏折叠也能触发
    observer = new ResizeObserver(() => resize())
    observer.observe(el)
  })

  onBeforeUnmount(() => {
    observer?.disconnect()
    observer = null
    chart?.dispose()
    chart = null
  })

  return { render, resize }
}
