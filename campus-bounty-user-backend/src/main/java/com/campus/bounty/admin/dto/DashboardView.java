package com.campus.bounty.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 运营看板聚合结果（设计 §8）。
 * <p>
 * 一个端点返回全部指标，前端一屏渲染（顶部数字卡片 + 状态分布饼图 + 7 天趋势折线），
 * 不做多次请求拼接——否则各卡片可能落在不同时间点，出现「订单总数 20 但分布加起来 19」这种
 * 自相矛盾的观感。所有数字都由 Service 在同一个只读事务里算出来。
 */
@Data
public class DashboardView {
    /** t_user 全量计数 */
    private long userTotal;
    /** 订单总数 */
    private long orderTotal;
    /** 已完成订单数 */
    private long orderCompleted;
    /** GMV（模拟成交额）= SUM(amount) WHERE status = COMPLETED */
    private BigDecimal gmv;
    /** 平台冻结总额 = SUM(wallet.frozen)，与 B3 冻结核对口径一致 */
    private BigDecimal frozenTotal;
    /** 待办：PENDING 举报数 */
    private long pendingReportCount;
    /** 待办：OPEN 申诉数 */
    private long openAppealCount;
    /** 订单状态分布，固定 6 项、按 {@code OrderStatus} 声明顺序，缺失状态补 0 */
    private List<StatusCount> statusDistribution;
    /** 近 7 天趋势，固定 7 项、日期升序（最后一项是今天），无数据的天补 0 */
    private List<TrendPoint> trend;

    /** 单个状态的人数——用 List 而非 Map，保证饼图图例顺序稳定（Map 顺序不可依赖）。 */
    @Data
    @AllArgsConstructor
    public static class StatusCount {
        private String status;
        private long count;
    }

    /**
     * 趋势的一天。created 与 completed 是两条独立序列：
     * created 取 created_at，completed 取 updated_at（库里没有 completed_at，见仓储注释）。
     */
    @Data
    @AllArgsConstructor
    public static class TrendPoint {
        private LocalDate date;
        private long created;
        private long completed;
    }
}
