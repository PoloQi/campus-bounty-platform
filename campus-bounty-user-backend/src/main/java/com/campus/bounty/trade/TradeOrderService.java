package com.campus.bounty.trade;

import com.campus.bounty.common.BizException;
import com.campus.bounty.trade.dto.OrderDetailView;
import com.campus.bounty.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TradeOrderService {
    private final TradeOrderRepository orderRepository;
    private final TradePostRepository postRepository;
    private final WalletService walletService;

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

    @Transactional
    public TradeOrder buy(Long postId, Long buyerId) {
        TradePost post = postRepository.findById(postId)
                .orElseThrow(() -> new BizException("服务不存在"));
        if (post.getType() != PostType.SERVICE) {
            throw new BizException("该帖子不是服务");
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
        order.setAmount(post.getPrice());
        order.setStatus(OrderStatus.IN_PROGRESS);
        orderRepository.save(order);

        walletService.freeze(buyerId, post.getPrice(), order.getId());
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
        order.setSellerId(accepterId);
        order.setStatus(OrderStatus.IN_PROGRESS);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        TradePost post = postRepository.findById(order.getPostId())
                .orElseThrow(() -> new BizException("悬赏不存在"));
        post.setStatus(PostStatus.LOCKED);
        postRepository.save(post);
        return order;
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
        return orderRepository.save(order);
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
        walletService.settle(order.getBuyerId(), order.getSellerId(), order.getAmount(), order.getId());
        order.setStatus(OrderStatus.COMPLETED);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        TradePost post = postRepository.findById(order.getPostId())
                .orElseThrow(() -> new BizException("悬赏不存在"));
        closePostIfBounty(post);
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
        walletService.unfreeze(order.getBuyerId(), order.getAmount(), order.getId());
        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        TradePost post = postRepository.findById(order.getPostId())
                .orElseThrow(() -> new BizException("悬赏不存在"));
        closePostIfBounty(post);
        return order;
    }

    public java.util.List<TradeOrder> ordersAsBuyer(Long userId) {
        return orderRepository.findByBuyerIdOrderByIdDesc(userId);
    }

    public java.util.List<TradeOrder> ordersAsSeller(Long userId) {
        return orderRepository.findBySellerIdOrderByIdDesc(userId);
    }
}
