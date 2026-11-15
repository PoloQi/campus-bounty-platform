package com.campus.bounty.admin;

import com.campus.bounty.admin.dto.AdminOrderDetailView;
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
import com.campus.bounty.user.UserRepository;
import com.campus.bounty.wallet.WalletLog;
import com.campus.bounty.wallet.WalletLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final AdminService adminService;
    private final TradeOrderRepository orderRepository;
    private final TradePostRepository postRepository;
    private final UserRepository userRepository;
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
}
