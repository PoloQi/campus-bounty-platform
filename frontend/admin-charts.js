import {init, use} from 'echarts/core';
import {LineChart, PieChart} from 'echarts/charts';
import {GridComponent} from 'echarts/components';
import {CanvasRenderer} from 'echarts/renderers';

use([LineChart, PieChart, GridComponent, CanvasRenderer]);

export function mountDashboardCharts(body, distribution, trend, colors, signal) {
  const pie = body.querySelector('.admin-pie');
  if (pie) {
    const chart = init(pie);
    const resize = new ResizeObserver(() => {
      if (!signal.aborted) chart.resize();
    });
    signal.addEventListener('abort', () => {
      resize.disconnect();
      chart.dispose();
    }, {once:true});
    chart.setOption({
      animation: false,
      series: [{
        type: 'pie',
        radius: '100%',
        startAngle: 90,
        clockwise: true,
        silent: true,
        stillShowZeroSum: false,
        label: {show: false},
        labelLine: {show: false},
        data: distribution.map(({status,total}, index) => ({
          name: status, value: total, itemStyle: {color: colors[index]},
        })),
      }],
    });
    resize.observe(pie);
  }

  const canvas = body.querySelector('.admin-trend');
  if (!canvas) return;
  // Keep the original drawing size. CSS scales this canvas with its container.
  const chart = init(canvas, null, {devicePixelRatio: 1});
  canvas.style.removeProperty('margin');
  signal.addEventListener('abort', () => chart.dispose(), {once:true});
  const values = trend.map(({total}) => total);
  const max = Math.max(...values, 1);
  const color = '#9f3216';
  chart.setOption({
    animation: false,
    grid: {left: 0, right: 0, top: 16, bottom: 16},
    xAxis: {type: 'category', show: false, boundaryGap: true, data: trend.map(day => day.orderDay)},
    yAxis: {
      type: 'value', min: 0, max, interval: max / 2,
      axisLine: {show: false},
      axisLabel: {show: false},
      axisTick: {show: false},
      splitLine: {lineStyle: {color: '#e8ded1'}},
    },
    series: [{
      type: 'line',
      data: values,
      symbol: 'circle',
      symbolSize: 8,
      showAllSymbol: true,
      clip: false,
      silent: true,
      lineStyle: {color, width: 3},
      itemStyle: {color},
    }],
  });
}
