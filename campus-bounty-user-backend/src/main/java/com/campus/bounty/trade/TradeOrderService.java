package com.campus.bounty.trade;

import com.campus.bounty.common.BizException;
import com.campus.bounty.notification.NotificationService;
import com.campus.bounty.trade.dto.OrderDetailView;
import com.campus.bounty.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TradeOrderService {
    static final String BOUNTY_EXPIRED = "BOUNTY_EXPIRED";

    private final TradeOrderRepository orderRepository;
    private final TradePostRepository postRepository;
    private final WalletService walletService;
    private final NotificationService notificationService;

    private TradeOrder locked(Long orderId) {
        return orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BizException("订单不存在"));
    }

    private void closePostIfBounty(TradePost post) {
        if (post.getType() == PostType.BOUNTY) {
            post.setStatus(PostStatus.CLOSED);
            postRepository.save(post);
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TradeOrder buy(Long postId, Long buyerId) {
        return buy(postId, buyerId, UUID.randomUUID());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TradeOrder buy(Long postId, Long buyerId, UUID requestId) {
        TradePost post = postRepository.findByIdForUpdate(postId)
                .orElseThrow(() -> new BizException("服务不存在"));
        if (post.getType() != PostType.SERVICE) {
            throw new BizException("该帖子不是服务");
        }
        String purchaseRequestId = requestId.toString();
        // The post lock serializes purchase attempts. Read committed sees an order written by the previous holder.
        var existing = orderRepository.findByPostIdAndBuyerIdAndPurchaseRequestId(postId, buyerId, purchaseRequestId);
        if (existing.isPresent()) {
            return existing.get();
        }
        if (post.getStatus() != PostStatus.OPEN) {
            throw new BizException("服务已下架");
        }
        if (buyerId.equals(post.getPublisherId())) {
            throw new BizException("不能购买自己发布的服务");
        }

        TradeOrder order = new TradeOrder();
        order.setPostId(postId);
        order.setBuyerId(buyerId);
        order.setSellerId(post.getPublisherId());
        order.setPurchaseRequestId(purchaseRequestId);
        order.setAmount(post.getPrice());
        order.setStatus(OrderStatus.IN_PROGRESS);
        orderRepository.save(order);

        walletService.freeze(buyerId, post.getPrice(), order.getId());
        notificationService.push(post.getPublisherId(), "ORDER_CREATED", "你的服务有新订单", order.getId());
        return order;
    }

    public OrderDetailView detailForParticipant(Long orderId, Long userId) {
        TradeOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BizException("订单不存在"));
        if (!userId.equals(order.getBuyerId()) && !userId.equals(order.getSellerId())) {
            throw new BizException("你不是该订单参与方");
        }
        TradePost post = postRepository.findById(order.getPostId())
                .orElseThrow(() -> new BizException("服务不存在"));
        return new OrderDetailView(order, post);
    }

    @Transactional
    public TradeOrder accept(Long orderId, Long accepterId) {
        TradeOrder order = locked(orderId);
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BizException("订单状态不允许此操作");
        }
        if (order.getBuyerId().equals(accepterId)) {
            throw new BizException("不能接自己的悬赏");
        }
        TradePost post = lockedPost(order);
        if (!isPendingBounty(order, post)) {
            throw new BizException("悬赏当前不可接单");
        }
        if (hasElapsed(post, BountyDeadline.now())) {
            throw new BizException("接单截止时间已过，请等待发布者延长");
        }
        order.setSellerId(accepterId);
        order.setStatus(OrderStatus.IN_PROGRESS);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        post.setStatus(PostStatus.LOCKED);
        postRepository.save(post);
        notificationService.push(order.getBuyerId(), "ORDER_ACCEPTED", "你的悬赏已被接单", order.getId());
        return order;
    }

    private TradePost lockedPost(TradeOrder order) {
        return postRepository.findByIdForUpdate(order.getPostId())
                .orElseThrow(() -> new BizException("悬赏不存在"));
    }

    private boolean isPendingBounty(TradeOrder order, TradePost post) {
        return order.getStatus() == OrderStatus.PENDING && order.getSellerId() == null
                && post.getType() == PostType.BOUNTY && post.getStatus() == PostStatus.OPEN;
    }

    private boolean hasElapsed(TradePost post, LocalDateTime now) {
        return post.getDeadline() != null && !post.getDeadline().isAfter(now);
    }

    @Transactional
    public TradeOrder extendDeadline(Long orderId, Long operatorId, LocalDateTime deadline) {
        TradeOrder order = locked(orderId);
        if (!order.getBuyerId().equals(operatorId)) {
            throw new BizException("只有发布者可延长接单截止时间");
        }
        TradePost post = lockedPost(order);
        LocalDateTime now = BountyDeadline.now();
        if (!isPendingBounty(order, post) || !hasElapsed(post, now)) {
            throw new BizException("仅到期且无人接单的悬赏可延长");
        }
        if (deadline == null || !deadline.isAfter(now)) {
            throw new BizException("新的接单截止时间必须晚于当前时间");
        }

        post.setDeadline(deadline);
        order.setUpdatedAt(LocalDateTime.now());
        return order;
    }

    @Transactional
    public void notifyDeadlineElapsed(Long orderId) {
        TradeOrder order = locked(orderId);
        TradePost post = lockedPost(order);
        LocalDateTime now = BountyDeadline.now();
        if (!isPendingBounty(order, post) || !hasElapsed(post, now)) {
            return;
        }

        // The order lock serializes the check and write across scanners and deadline extensions.
        if (notificationService.hasNotificationSince(order.getBuyerId(), BOUNTY_EXPIRED, orderId, post.getDeadline())) {
            return;
        }
        notificationService.push(order.getBuyerId(), BOUNTY_EXPIRED,
                "悬赏「" + post.getTitle() + "」已超过接单截止时间。请取消悬赏或延长截止时间。", orderId, now);
    }

    @Transactional
    public TradeOrder deliver(Long orderId, Long operatorId) {
        TradeOrder order = locked(orderId);
        if (order.getStatus() != OrderStatus.IN_PROGRESS) {
            throw new BizException("订单状态不允许此操作");
        }
        if (!operatorId.equals(order.getSellerId())) {
            throw new BizException("只有接单者可标记交付");
        }
        order.setStatus(OrderStatus.CONFIRMING);
        order.setUpdatedAt(LocalDateTime.now());
        TradeOrder saved = orderRepository.save(order);
        notificationService.push(order.getBuyerId(), "ORDER_DELIVERED", "对方已交付，请确认", order.getId());
        return saved;
    }

    @Transactional
    public TradeOrder confirm(Long orderId, Long operatorId) {
        TradeOrder order = locked(orderId);
        if (order.getStatus() != OrderStatus.CONFIRMING) {
            throw new BizException("订单状态不允许此操作");
        }
        if (!operatorId.equals(order.getBuyerId())) {
            throw new BizException("只有发布者可确认完成");
        }
        completeEscrow(order, OrderStatus.COMPLETED);
        notificationService.push(order.getSellerId(), "ORDER_COMPLETED", "订单已完成，赏金已到账", order.getId());
        return order;
    }

    @Transactional
    public TradeOrder cancel(Long orderId, Long operatorId) {
        TradeOrder order = locked(orderId);
        if (!operatorId.equals(order.getBuyerId())) {
            throw new BizException("只有发布者可取消");
        }
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.IN_PROGRESS) {
            throw new BizException("订单状态不允许取消");
        }
        completeEscrow(order, OrderStatus.CANCELLED);
        if (order.getSellerId() != null) {
            notificationService.push(order.getSellerId(), "ORDER_CANCELLED", "订单已被取消", order.getId());
        }
        return order;
    }

    public java.util.List<TradeOrder> ordersAsBuyer(Long userId) {
        return orderRepository.findByBuyerIdOrderByIdDesc(userId);
    }

    // Callers hold the order lock and have validated the transition.
    void completeEscrow(TradeOrder order, OrderStatus terminalStatus) {
        switch (terminalStatus) {
            case COMPLETED -> walletService.settle(order.getBuyerId(), order.getSellerId(),
                    order.getAmount(), order.getId());
            case CANCELLED -> walletService.unfreeze(order.getBuyerId(), order.getAmount(), order.getId());
            default -> throw new IllegalArgumentException("Expected a completed or cancelled order");
        }
        order.setStatus(terminalStatus);
        order.setUpdatedAt(LocalDateTime.now());
        TradePost post = postRepository.findById(order.getPostId())
                .orElseThrow(() -> new BizException("交易帖不存在"));
        closePostIfBounty(post);
    }

    public java.util.List<TradeOrder> ordersAsSeller(Long userId) {
        return orderRepository.findBySellerIdOrderByIdDesc(userId);
    }
}
