package com.campus.bounty.admin;

import com.campus.bounty.admin.dto.DashboardView;
import com.campus.bounty.trade.AppealRepository;
import com.campus.bounty.trade.OrderStatus;
import com.campus.bounty.trade.TradeOrderRepository;
import com.campus.bounty.user.UserRepository;
import com.campus.bounty.wallet.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据看板（设计 §8）。全平台汇总，只读。
 * <p>
 * 只有一个方法，但它是本项目查询最多的一处，所以有两条硬约束：
 * <ol>
 *   <li><b>整块包装在一个只读事务里</b>：十个计数必须落在同一个数据库快照上，
 *       否则中途有人下单，就会出现「状态分布加起来 19、订单总数却是 20」的自相矛盾。</li>
 *   <li><b>分组计数下推到数据库</b>：趋势和状态分布都用 group by 一次取回，
 *       不把订单表拉进内存再数——看板是高频打开的页面，不能随数据量线性变慢。</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class AdminDashboardService {
    /** 趋势窗口：含今天在内的 7 天。 */
    private static final int TREND_DAYS = 7;
    private static final String REPORT_PENDING = "PENDING";
    private static final String APPEAL_OPEN = "OPEN";

    private final AdminService adminService;
    private final UserRepository userRepository;
    private final TradeOrderRepository orderRepository;
    private final WalletRepository walletRepository;
    private final ReportRepository reportRepository;
    private final AppealRepository appealRepository;

    @Transactional(readOnly = true)
    public DashboardView overview(Long adminId) {
        adminService.requireAdmin(adminId);

        LocalDate today = LocalDate.now();
        LocalDate firstDay = today.minusDays(TREND_DAYS - 1);
        LocalDateTime windowStart = firstDay.atStartOfDay();

        Map<OrderStatus, Long> distribution = loadDistribution();
        List<Object[]> createdRows = orderRepository.countCreatedByDay(windowStart);
        List<Object[]> completedRows = orderRepository.countUpdatedByDay(OrderStatus.COMPLETED, windowStart);

        DashboardView view = new DashboardView();
        view.setUserTotal(userRepository.count());
        view.setOrderTotal(orderRepository.count());
        view.setOrderCompleted(distribution.getOrDefault(OrderStatus.COMPLETED, 0L));
        // GMV 复用 B3 的「按状态求和」，口径与冻结核对页完全一致，避免两处各写一条 SQL 后走偏
        view.setGmv(orderRepository.sumAmountByStatusIn(List.of(OrderStatus.COMPLETED)));
        view.setFrozenTotal(walletRepository.totalFrozen());
        view.setPendingReportCount(reportRepository.countByStatus(REPORT_PENDING));
        view.setOpenAppealCount(appealRepository.countByStatus(APPEAL_OPEN));
        view.setStatusDistribution(toStatusCounts(distribution));
        view.setTrend(buildTrend(firstDay, createdRows, completedRows));
        return view;
    }

    /** 全表按状态分组：一次查询代替 6 次 count，且天然包含 DISPUTED 这种低频状态。 */
    private Map<OrderStatus, Long> loadDistribution() {
        Map<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
        for (Object[] row : orderRepository.countGroupByStatus()) {
            counts.put((OrderStatus) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    /**
     * 6 态补齐。用 EnumMap 遍历 {@code OrderStatus.values()} 而不是遍历查询结果：
     * 结果顺序由数据库决定，饼图图例会随机跳动；且没有订单的状态根本不会出现在结果里，
     * 前端就得自己判断 key 是否存在。这里一次性给全，前端可以直接 map。
     */
    private List<DashboardView.StatusCount> toStatusCounts(Map<OrderStatus, Long> distribution) {
        List<DashboardView.StatusCount> list = new ArrayList<>(OrderStatus.values().length);
        for (OrderStatus status : OrderStatus.values()) {
            list.add(new DashboardView.StatusCount(status.name(),
                    distribution.getOrDefault(status, 0L)));
        }
        return list;
    }

    /**
     * 7 天趋势：先把 7 个日期全部铺成 0，再用查询结果覆盖。
     * 「缺日期补 0」必须在后端做——前端拿到长度 3 的数组去画 7 天折线，
     * 会把三天的点均匀铺满横轴，看起来像天天都有单。
     */
    private List<DashboardView.TrendPoint> buildTrend(LocalDate firstDay,
                                                      List<Object[]> createdRows,
                                                      List<Object[]> completedRows) {
        Map<LocalDate, long[]> buckets = new HashMap<>();
        for (int i = 0; i < TREND_DAYS; i++) {
            buckets.put(firstDay.plusDays(i), new long[2]);
        }
        fill(buckets, createdRows, 0);
        fill(buckets, completedRows, 1);

        List<DashboardView.TrendPoint> trend = new ArrayList<>(TREND_DAYS);
        for (int i = 0; i < TREND_DAYS; i++) {
            LocalDate date = firstDay.plusDays(i);
            long[] bucket = buckets.get(date);
            trend.add(new DashboardView.TrendPoint(date, bucket[0], bucket[1]));
        }
        return trend;
    }

    /**
     * 把 [year, month, day, count] 行灌进桶里。
     * 不在窗口内的行直接丢弃：查询已按 from 过滤，理论上不会有，但边界日的时区换算
     * 一旦出偏差就会出现「多一行 → NPE」，宁可静默丢弃也不要让看板 500。
     */
    private void fill(Map<LocalDate, long[]> buckets, List<Object[]> rows, int slot) {
        for (Object[] row : rows) {
            LocalDate date = LocalDate.of(
                    ((Number) row[0]).intValue(),
                    ((Number) row[1]).intValue(),
                    ((Number) row[2]).intValue());
            long[] bucket = buckets.get(date);
            if (bucket != null) {
                bucket[slot] = ((Number) row[3]).longValue();
            }
        }
    }
}
