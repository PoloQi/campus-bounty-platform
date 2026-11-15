package com.campus.bounty.admin;

import com.campus.bounty.admin.dto.AppealDetailView;
import com.campus.bounty.admin.dto.AppealListItem;
import com.campus.bounty.admin.dto.UserBrief;
import com.campus.bounty.common.BizException;
import com.campus.bounty.notification.NotificationService;
import com.campus.bounty.trade.Appeal;
import com.campus.bounty.trade.AppealRepository;
import com.campus.bounty.trade.OrderStatus;
import com.campus.bounty.trade.PostStatus;
import com.campus.bounty.trade.PostType;
import com.campus.bounty.trade.TradeOrder;
import com.campus.bounty.trade.TradeOrderRepository;
import com.campus.bounty.trade.TradePost;
import com.campus.bounty.trade.TradePostRepository;
import com.campus.bounty.user.User;
import com.campus.bounty.user.UserRepository;
import com.campus.bounty.wallet.WalletLog;
import com.campus.bounty.wallet.WalletLogRepository;
import com.campus.bounty.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * B6 申诉仲裁。
 * <p>
 * 这是整个管理端**唯一会动钱、也是唯一会改订单终态**的入口（管理端对订单只读，见设计 §6）。
 * 因此资金动作一律复用 {@link WalletService#settle} / {@link WalletService#unfreeze}，
 * 不在这里新写任何余额加减——那会绕开冻结校验与流水记录。
 */
@Service
@RequiredArgsConstructor
public class AdminAppealService {
    /** 裁决方向。取值与 {@code t_appeal.outcome}、审计动作一一对应，是数据契约，不要随意改名。 */
    public static final String RELEASE_TO_SELLER = "RELEASE_TO_SELLER";
    public static final String REFUND_TO_BUYER = "REFUND_TO_BUYER";

    private final AdminService adminService;
    private final AppealRepository appealRepository;
    private final TradeOrderRepository orderRepository;
    private final TradePostRepository postRepository;
    private final UserRepository userRepository;
    private final WalletLogRepository walletLogRepository;
    private final WalletService walletService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    /**
     * 申诉列表。status 为空即全量，OPEN 由仓储层排在最前。
     * 用三条批量查询（订单/帖子/用户）代替逐行查，避免列表上百行时打出 3N 次 SQL。
     */
    @Transactional(readOnly = true)
    public List<AppealListItem> list(Long adminId, String status) {
        adminService.requireAdmin(adminId);
        String normalized = status == null || status.isBlank() ? null : status.trim();
        List<Appeal> appeals = appealRepository.searchForAdmin(normalized);
        if (appeals.isEmpty()) {
            return List.of();
        }

        Map<Long, TradeOrder> orders = orderRepository
                .findAllById(appeals.stream().map(Appeal::getOrderId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(TradeOrder::getId, Function.identity()));
        Map<Long, TradePost> posts = postRepository
                .findAllById(orders.values().stream().map(TradeOrder::getPostId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(TradePost::getId, Function.identity()));
        Map<Long, User> users = userRepository
                .findAllById(appeals.stream().map(Appeal::getAppellantId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return appeals.stream().map(appeal -> {
            TradeOrder order = orders.get(appeal.getOrderId());
            TradePost post = order == null ? null : posts.get(order.getPostId());
            return AppealListItem.of(appeal, order, post, users.get(appeal.getAppellantId()));
        }).toList();
    }

    /**
     * 仲裁台详情。只读事务让订单、双方、流水落在同一快照上，
     * 免得读到「申诉仍 OPEN 但钱已经不在冻结里」的中间态。
     */
    @Transactional(readOnly = true)
    public AppealDetailView detail(Long adminId, Long appealId) {
        adminService.requireAdmin(adminId);
        Appeal appeal = requireAppeal(appealId);
        TradeOrder order = orderRepository.findById(appeal.getOrderId())
                .orElseThrow(() -> new BizException("订单不存在"));
        TradePost post = postRepository.findById(order.getPostId()).orElse(null);
        UserBrief buyer = UserBrief.of(userRepository.findById(order.getBuyerId()).orElse(null));
        UserBrief seller = order.getSellerId() == null
                ? null
                : UserBrief.of(userRepository.findById(order.getSellerId()).orElse(null));
        UserBrief appellant = UserBrief.of(userRepository.findById(appeal.getAppellantId()).orElse(null));
        List<WalletLog> walletLogs = walletLogRepository.findByOrderIdOrderByIdAsc(order.getId());
        return new AppealDetailView(appeal, order, post, appellant, buyer, seller,
                roleOf(appeal, order), walletLogs);
    }

    /** 判收款方胜：解冻买方托管金 → 打入卖方余额，订单 COMPLETED。 */
    @Transactional
    public Appeal release(Long adminId, Long appealId, String comment) {
        return resolve(adminId, appealId, RELEASE_TO_SELLER, comment);
    }

    /** 判出资方胜：托管金退回买方余额，订单 CANCELLED。 */
    @Transactional
    public Appeal refund(Long adminId, Long appealId, String comment) {
        return resolve(adminId, appealId, REFUND_TO_BUYER, comment);
    }

    /**
     * 仲裁主流程。顺序不可调整：
     * 校验 → 锁订单 → 再判状态 → 动钱 → 改订单 → 关帖 → 回写申诉 → 审计 → 通知。
     * <p>
     * 先锁订单再校验状态，是为了防「两个管理员同时点裁决」：第二个事务会阻塞在锁上，
     * 等第一个提交后读到 DISPUTED 已变成 COMPLETED，从而在动钱之前就被拦下。
     */
    private Appeal resolve(Long adminId, Long appealId, String outcome, String comment) {
        adminService.requireAdmin(adminId);
        if (comment == null || comment.isBlank()) {
            throw new BizException("请填写处理意见");
        }

        Appeal appeal = requireAppeal(appealId);
        if (!"OPEN".equals(appeal.getStatus())) {
            throw new BizException("该申诉已处理，不能重复裁决");
        }

        TradeOrder order = orderRepository.findByIdForUpdate(appeal.getOrderId())
                .orElseThrow(() -> new BizException("订单不存在"));
        if (order.getStatus() != OrderStatus.DISPUTED) {
            throw new BizException("订单当前不在纠纷中，无法裁决");
        }

        boolean release = RELEASE_TO_SELLER.equals(outcome);
        if (release) {
            if (order.getSellerId() == null) {
                throw new BizException("订单缺少收款方，无法判付");
            }
            walletService.settle(order.getBuyerId(), order.getSellerId(), order.getAmount(), order.getId());
        } else {
            walletService.unfreeze(order.getBuyerId(), order.getAmount(), order.getId());
        }

        order.setStatus(release ? OrderStatus.COMPLETED : OrderStatus.CANCELLED);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        closePostIfBounty(order.getPostId());

        appeal.setStatus("RESOLVED");
        appeal.setOutcome(outcome);
        appeal.setAdminId(adminId);
        appeal.setAdminComment(comment);
        appeal.setHandledAt(LocalDateTime.now());
        appealRepository.save(appeal);

        String amount = order.getAmount().stripTrailingZeros().toPlainString();
        auditLogService.record(adminId,
                release ? AuditAction.APPEAL_RELEASE : AuditAction.APPEAL_REFUND,
                "APPEAL", appealId,
                (release ? "仲裁判付：订单#" : "仲裁退款：订单#") + order.getId()
                        + "，金额 " + amount + "，意见：" + comment);

        notifyBothSides(order, release, amount);
        return appeal;
    }

    /**
     * 通知买卖双方。文案固定在 200 字以内（t_notification.content 是 VARCHAR(200)），
     * refId 用订单 id 而不是申诉 id：学生端只有订单详情页能继续看这笔纠纷。
     */
    private void notifyBothSides(TradeOrder order, boolean release, String amount) {
        String content = release
                ? "仲裁结果：订单 #" + order.getId() + " 判付收款方，" + amount + " 元已到账"
                : "仲裁结果：订单 #" + order.getId() + " 判退出资方，" + amount + " 元已退回";
        notificationService.push(order.getBuyerId(), "APPEAL_RESOLVED", content, order.getId());
        if (order.getSellerId() != null && !order.getSellerId().equals(order.getBuyerId())) {
            notificationService.push(order.getSellerId(), "APPEAL_RESOLVED", content, order.getId());
        }
    }

    /** 只有悬赏帖才关帖：服务帖是一帖多单，一单纠纷不影响其他人继续下单（设计 §4.4）。 */
    private void closePostIfBounty(Long postId) {
        TradePost post = postRepository.findById(postId).orElse(null);
        if (post != null && post.getType() == PostType.BOUNTY && post.getStatus() != PostStatus.CLOSED) {
            post.setStatus(PostStatus.CLOSED);
            postRepository.save(post);
        }
    }

    private String roleOf(Appeal appeal, TradeOrder order) {
        if (appeal.getAppellantId().equals(order.getBuyerId())) {
            return "BUYER";
        }
        if (appeal.getAppellantId().equals(order.getSellerId())) {
            return "SELLER";
        }
        return "UNKNOWN";
    }

    private Appeal requireAppeal(Long appealId) {
        return appealRepository.findById(appealId)
                .orElseThrow(() -> new BizException("申诉不存在"));
    }
}
