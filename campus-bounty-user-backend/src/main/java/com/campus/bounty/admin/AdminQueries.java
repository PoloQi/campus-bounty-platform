package com.campus.bounty.admin;

import com.campus.bounty.common.BizException;
import com.campus.bounty.trade.Appeal;
import com.campus.bounty.trade.AppealRepository;
import com.campus.bounty.trade.OrderStatus;
import com.campus.bounty.trade.PostType;
import com.campus.bounty.trade.TradeOrder;
import com.campus.bounty.trade.TradeOrderRepository;
import com.campus.bounty.trade.TradePost;
import com.campus.bounty.trade.TradePostRepository;
import com.campus.bounty.user.User;
import com.campus.bounty.user.UserRepository;
import com.campus.bounty.wallet.Wallet;
import com.campus.bounty.wallet.WalletLog;
import com.campus.bounty.wallet.WalletLogRepository;
import com.campus.bounty.wallet.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminQueries {
    private static final int PAGE_SIZE = 20;
    private final JdbcClient jdbc;
    private final TradePostRepository posts;
    private final TradeOrderRepository orders;
    private final UserRepository users;
    private final WalletRepository wallets;
    private final WalletLogRepository walletLogs;
    private final AppealRepository appeals;

    public Dashboard dashboard() {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(6);
        DashboardTotals totals = jdbc.sql("""
                select (select count(*) from t_user where role = 'STUDENT') as students,
                    (select count(*) from trade_order) as orders,
                    (select coalesce(sum(amount), 0) from trade_order where status = 'COMPLETED') as gmv,
                    (select coalesce(sum(frozen), 0) from wallet) as frozen,
                    (select count(*) from t_appeal where status = 'OPEN') as open_appeals,
                    (select count(*) from t_report where status = 'PENDING') as pending_reports
                """).query(DashboardTotals.class).single();
        Map<String, Long> counts = jdbc.sql("""
                select status, count(*) as total from trade_order group by status
                """).query(StatusCount.class).list().stream()
                .collect(Collectors.toMap(StatusCount::status, StatusCount::total));
        List<StatusCount> distribution = Arrays.stream(OrderStatus.values())
                .map(status -> new StatusCount(status.name(), counts.getOrDefault(status.name(), 0L)))
                .toList();
        Map<LocalDate, Long> recent = jdbc.sql("""
                select cast(created_at as date) as order_day, count(*) as total
                from trade_order where created_at >= :start and created_at < :end
                group by cast(created_at as date)
                """).param("start", start.atStartOfDay()).param("end", today.plusDays(1).atStartOfDay())
                .query(DayCount.class).list().stream()
                .collect(Collectors.toMap(DayCount::orderDay, DayCount::total));
        List<DayCount> trend = IntStream.rangeClosed(0, 6).mapToObj(start::plusDays)
                .map(day -> new DayCount(day, recent.getOrDefault(day, 0L))).toList();
        return new Dashboard(totals, distribution, trend);
    }

    public Page<OrderRow> orders(OrderStatus status, PostType type, Long buyerId, Long sellerId,
                                 LocalDate from, LocalDate through, int page) {
        var query = filter("""
                select o.id, p.type, p.id as post_id, p.title, o.buyer_id, o.seller_id,
                    o.amount, o.status, o.created_at
                from trade_order o join trade_post p on p.id = o.post_id where 1 = 1
                """).equal("o.status", name(status)).equal("p.type", name(type))
                .equal("o.buyer_id", buyerId).equal("o.seller_id", sellerId)
                .dates("o.created_at", from, through);
        return query.page("o.id desc", page, OrderRow.class);
    }

    public OrderDetail order(Long id) {
        TradeOrder order = orders.findById(id).orElseThrow(() -> new BizException("订单不存在"));
        TradePost post = posts.findById(order.getPostId()).orElseThrow(() -> new BizException("交易帖不存在"));
        List<WalletLog> logs = walletLogs.findByOrderIdOrderByIdAsc(id);
        List<Appeal> caseHistory = appeals.findByOrderIdOrderByIdDesc(id);
        return new OrderDetail(order, post, user(order.getBuyerId()), user(order.getSellerId()), logs,
                caseHistory, timeline(order, post, logs, caseHistory));
    }

    private UserRow user(Long id) {
        return id == null ? null : users.findById(id).map(UserRow::from).orElse(null);
    }

    private List<TimelineEvent> timeline(TradeOrder order, TradePost post, List<WalletLog> logs, List<Appeal> caseHistory) {
        var events = new ArrayList<TimelineEvent>();
        boolean service = post.getType() == PostType.SERVICE;
        events.add(new TimelineEvent(service ? "ORDER_CREATED" : "BOUNTY_PUBLISHED",
                order.getCreatedAt(), order.getBuyerId(), post.getTitle()));
        events.addAll(jdbc.sql("""
                select type, created_at,
                    case when type in ('ORDER_ACCEPTED', 'ORDER_DELIVERED') then :seller else :buyer end as actor_id,
                    content as details
                from t_notification
                where ref_id = :order and type in ('ORDER_ACCEPTED', 'ORDER_DELIVERED', 'BOUNTY_EXPIRED')
                  and user_id = :buyer
                order by id
                """).param("order", order.getId()).param("seller", order.getSellerId(), java.sql.Types.BIGINT)
                .param("buyer", order.getBuyerId()).query(TimelineEvent.class).list());
        for (Appeal appeal : caseHistory) {
            events.add(new TimelineEvent("APPEAL_CREATED", appeal.getCreatedAt(), appeal.getAppellantId(), appeal.getReason()));
            if ("WITHDRAWN".equals(appeal.getStatus())) {
                events.add(new TimelineEvent("APPEAL_WITHDRAWN", appeal.getHandledAt(), appeal.getAppellantId(), "已恢复原订单状态"));
            } else if ("RESOLVED".equals(appeal.getStatus())) {
                String result = "RELEASE_TO_SELLER".equals(appeal.getOutcome()) ? "打款给卖家" : "退款给买家";
                events.add(new TimelineEvent("APPEAL_RESOLVED", appeal.getHandledAt(), appeal.getAdminId(),
                        result + " · " + appeal.getAdminComment()));
            }
        }
        for (WalletLog log : logs) {
            events.add(new TimelineEvent(log.getType(), log.getCreatedAt(), log.getUserId(), log.getRemark()));
        }
        if (caseHistory.stream().noneMatch(appeal -> "RESOLVED".equals(appeal.getStatus()))) {
            if (order.getStatus() == OrderStatus.COMPLETED) {
                events.add(new TimelineEvent("ORDER_COMPLETED", order.getUpdatedAt(), order.getBuyerId(), "出资方确认完成"));
            } else if (order.getStatus() == OrderStatus.CANCELLED) {
                events.add(new TimelineEvent("ORDER_CANCELLED", order.getUpdatedAt(), order.getBuyerId(), "出资方取消订单"));
            }
        }
        events.sort(Comparator.comparing(TimelineEvent::createdAt, Comparator.nullsLast(Comparator.naturalOrder())));
        return events;
    }

    public Page<ReportRow> reports(String status, String targetType, int page) {
        return reportQuery().equal("r.status", status).equal("r.target_type", targetType)
                .page("case when r.status = 'PENDING' then 0 else 1 end, r.id desc", page, ReportRow.class);
    }

    public ReportRow report(Long id) {
        var query = reportQuery().equal("r.id", id);
        return jdbc.sql(query.sql.toString()).params(query.params).query(ReportRow.class).optional()
                .orElseThrow(() -> new BizException("举报不存在"));
    }

    private Query reportQuery() {
        return filter("""
                select r.id, r.target_type, r.target_id, r.reporter_id, r.reason, r.status, r.created_at,
                    x.result, x.comment, x.admin_id, x.handled_at, x.moderation_action
                from t_report r left join t_report_resolution x on x.report_id = r.id where 1 = 1
                """);
    }

    public ModerationTarget target(String type, Long id) {
        String query = switch (type) {
            case "POST" -> "select id, content as summary, status from t_post where id = :id";
            case "TRADE_POST" -> "select id, title as summary, status from trade_post where id = :id";
            case "USER" -> "select id, username as summary, status from t_user where id = :id";
            default -> throw new BizException("举报对象类型不合法");
        };
        return jdbc.sql(query).param("id", id).query(ModerationTarget.class).optional()
                .orElseThrow(() -> new BizException("治理对象不存在"));
    }

    public Page<UserRow> users(String keyword, String status, int page) {
        Query query = filter("select id, username, nickname, role, status, created_at from t_user where 1 = 1")
                .equal("status", status);
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.strip().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
            query.add("""
                    (lower(username) like :keyword escape '!' or lower(nickname) like :keyword escape '!'
                        or cast(id as char(20)) = :idKeyword)
                    """).params.putAll(Map.of("keyword", "%" + value + "%", "idKeyword", value));
        }
        return query.page("id desc", page, UserRow.class);
    }

    public FundTotals funds() {
        FundBalances balances = jdbc.sql("""
                select (select coalesce(sum(frozen), 0) from wallet) as frozen,
                    (select coalesce(sum(amount), 0) from trade_order
                        where status in ('PENDING', 'IN_PROGRESS', 'CONFIRMING', 'DISPUTED')) as expected
                """).query(FundBalances.class).single();
        return new FundTotals(balances.frozen(), balances.expected(), balances.frozen().subtract(balances.expected()));
    }

    public Wallet wallet(Long userId) {
        return wallets.findByUserId(userId).orElse(null);
    }

    public Page<WalletLog> ledger(Long userId, String type, Long orderId, LocalDate from, LocalDate through, int page) {
        return filter("""
                select id, user_id, type, amount, balance_after, order_id, remark, created_at
                from wallet_log where 1 = 1
                """).equal("user_id", userId).equal("type", type).equal("order_id", orderId)
                .dates("created_at", from, through).page("id desc", page, WalletLog.class);
    }

    public Page<AuditRow> audit(Long adminId, String action, LocalDate from, LocalDate through, int page) {
        return filter("""
                select id, admin_id, action, target_type, target_id, details, created_at
                from admin_log where 1 = 1
                """).equal("admin_id", adminId).equal("action", action)
                .dates("created_at", from, through).page("id desc", page, AuditRow.class);
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private Query filter(String sql) {
        return new Query(sql);
    }

    // Column and sort fragments are fixed in this class. Request values are always bound parameters.
    private final class Query {
        private final StringBuilder sql;
        private final Map<String, Object> params = new java.util.HashMap<>();

        private Query(String sql) {
            this.sql = new StringBuilder(sql);
        }

        private Query add(String clause) {
            sql.append(" and ").append(clause);
            return this;
        }

        private Query equal(String column, Object value) {
            if (value != null) {
                String key = "filter" + params.size();
                add(column + " = :" + key);
                params.put(key, value);
            }
            return this;
        }

        private Query dates(String column, LocalDate from, LocalDate through) {
            if (from != null && through != null && through.isBefore(from)) {
                throw new BizException("结束日期不能早于开始日期");
            }
            if (from != null) {
                add(column + " >= :fromDate");
                params.put("fromDate", from.atStartOfDay());
            }
            if (through != null) {
                add(column + " < :endDate");
                params.put("endDate", through.plusDays(1).atStartOfDay());
            }
            return this;
        }

        private <T> Page<T> page(String order, int page, Class<T> type) {
            if (page < 0) {
                throw new BizException("页码不能为空或负数");
            }
            sql.append(" order by ").append(order).append(" limit :limit offset :offset");
            params.put("limit", PAGE_SIZE + 1);
            params.put("offset", (long) page * PAGE_SIZE);
            List<T> rows = jdbc.sql(sql.toString()).params(params).query(type).list();
            boolean hasNext = rows.size() > PAGE_SIZE;
            return new Page<>(hasNext ? rows.subList(0, PAGE_SIZE) : rows, page, hasNext);
        }
    }

    public record Page<T>(List<T> items, int page, boolean hasNext) {
    }

    public record Dashboard(DashboardTotals totals, List<StatusCount> distribution, List<DayCount> trend) {
    }

    public record DashboardTotals(long students, long orders, BigDecimal gmv, BigDecimal frozen,
                                  long openAppeals, long pendingReports) {
    }

    public record StatusCount(String status, long total) {
    }

    public record DayCount(LocalDate orderDay, long total) {
    }

    public record OrderRow(Long id, String type, Long postId, String title, Long buyerId, Long sellerId,
                           BigDecimal amount, String status, LocalDateTime createdAt) {
    }

    public record UserRow(Long id, String username, String nickname, String role, String status, LocalDateTime createdAt) {
        static UserRow from(User user) {
            return new UserRow(user.getId(), user.getUsername(), user.getNickname(), user.getRole(), user.getStatus(),
                    user.getCreatedAt());
        }
    }

    public record OrderDetail(TradeOrder order, TradePost post, UserRow buyer, UserRow seller, List<WalletLog> logs,
                              List<Appeal> appeals, List<TimelineEvent> timeline) {
    }

    public record TimelineEvent(String type, LocalDateTime createdAt, Long actorId, String details) {
    }

    public record ReportRow(Long id, String targetType, Long targetId, Long reporterId, String reason, String status,
                            LocalDateTime createdAt, String result, String comment, Long adminId, LocalDateTime handledAt,
                            String moderationAction) {
    }

    public record ModerationTarget(Long id, String summary, String status) {
    }

    private record FundBalances(BigDecimal frozen, BigDecimal expected) {
    }

    public record FundTotals(BigDecimal frozen, BigDecimal expected, BigDecimal difference) {
    }

    public record AuditRow(Long id, Long adminId, String action, String targetType, Long targetId, String details,
                           LocalDateTime createdAt) {
    }
}
