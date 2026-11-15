package com.campus.bounty.admin;

import com.campus.bounty.admin.dto.AdminOrderDetailView;
import com.campus.bounty.admin.dto.FrozenSummaryView;
import com.campus.bounty.admin.dto.UserBrief;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端只读查询服务。
 * <p>
 * 与 {@link AdminService} 的分工：AdminService 放会改变平台状态的写动作（删帖/下架/封禁），
 * 本类只做跨聚合的读编排。管理端「对订单只读」是设计约束——改变订单的唯一入口是申诉仲裁。
 */
@Service
@RequiredArgsConstructor
public class AdminQueryService {
    /** 在途状态：钱被托管中，既未结算给卖方也未退回买方。 */
    private static final List<OrderStatus> IN_FLIGHT_STATUSES = List.of(
            OrderStatus.PENDING, OrderStatus.IN_PROGRESS,
            OrderStatus.CONFIRMING, OrderStatus.DISPUTED);

    private final AdminService adminService;
    private final TradeOrderRepository orderRepository;
    private final TradePostRepository postRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final WalletLogRepository walletLogRepository;
    private final AppealRepository appealRepository;

    /** 订单筛选列表。课程规模不做后端分页，前端 Element Plus 自行分页（设计 §6）。 */
    public List<TradeOrder> listOrders(Long adminId, OrderStatus status, PostType postType,
                                       Long buyerId, Long sellerId,
                                       LocalDateTime from, LocalDateTime to) {
        adminService.requireAdmin(adminId);
        return orderRepository.searchForAdmin(status, postType, buyerId, sellerId, from, to);
    }

    /**
     * 订单详情时间线聚合。
     * 只读事务：多次查询落在同一快照上，避免读到「订单已结算但流水还没刷出来」的中间态。
     */
    @Transactional(readOnly = true)
    public AdminOrderDetailView orderDetail(Long adminId, Long orderId) {
        adminService.requireAdmin(adminId);
        TradeOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BizException("订单不存在"));
        TradePost post = postRepository.findById(order.getPostId()).orElse(null);
        UserBrief buyer = UserBrief.of(userRepository.findById(order.getBuyerId()).orElse(null));
        UserBrief seller = order.getSellerId() == null
                ? null
                : UserBrief.of(userRepository.findById(order.getSellerId()).orElse(null));
        List<WalletLog> walletLogs = walletLogRepository.findByOrderIdOrderByIdAsc(orderId);
        List<Appeal> appeals = appealRepository.findByOrderIdOrderByIdDesc(orderId);
        return new AdminOrderDetailView(order, post, buyer, seller, walletLogs, appeals);
    }

    // ---------- B3 用户与资金只读 ----------

    /** 用户筛选列表。返回白名单摘要，绝不返回 User 实体（会带出 passwordHash）。 */
    public List<UserBrief> listUsers(Long adminId, String keyword, String role, String status,
                                     LocalDateTime from, LocalDateTime to) {
        adminService.requireAdmin(adminId);
        String normalized = keyword == null || keyword.isBlank() ? null : keyword.trim();
        return userRepository.searchForAdmin(normalized, role, status, from, to).stream()
                .map(UserBrief::of)
                .toList();
    }

    /**
     * 单用户钱包。用户不存在报错（它是资源标识，不是筛选条件）；
     * 钱包缺失时返回零值视图而不落库——管理端只读，不允许借查询顺手建数据。
     */
    @Transactional(readOnly = true)
    public Wallet userWallet(Long adminId, Long userId) {
        adminService.requireAdmin(adminId);
        requireUser(userId);
        return walletRepository.findByUserId(userId).orElseGet(() -> {
            Wallet empty = new Wallet();
            empty.setUserId(userId);
            return empty;
        });
    }

    /** 单用户全部流水，倒序。 */
    @Transactional(readOnly = true)
    public List<WalletLog> userWalletLogs(Long adminId, Long userId) {
        adminService.requireAdmin(adminId);
        requireUser(userId);
        return walletLogRepository.findByUserIdOrderByIdDesc(userId);
    }

    /** 全平台流水筛选。userId 只是筛选条件，传不存在的 id 返回空列表而非报错。 */
    public List<WalletLog> platformWalletLogs(Long adminId, Long userId, String type,
                                              LocalDateTime from, LocalDateTime to) {
        adminService.requireAdmin(adminId);
        String normalized = type == null || type.isBlank() ? null : type.trim();
        return walletLogRepository.searchForAdmin(userId, normalized, from, to);
    }

    /** 冻结总额核对：两个 SUM 必须在同一快照上算，否则中途有人冻结就会得出假差额。 */
    @Transactional(readOnly = true)
    public FrozenSummaryView frozenSummary(Long adminId) {
        adminService.requireAdmin(adminId);
        BigDecimal frozenTotal = walletRepository.totalFrozen();
        BigDecimal inFlightAmount = orderRepository.sumAmountByStatusIn(IN_FLIGHT_STATUSES);
        long inFlightOrderCount = orderRepository.countByStatusIn(IN_FLIGHT_STATUSES);
        return FrozenSummaryView.of(frozenTotal, inFlightAmount, inFlightOrderCount);
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BizException("用户不存在"));
    }
}
