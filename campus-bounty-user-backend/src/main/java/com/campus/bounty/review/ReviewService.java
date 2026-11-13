package com.campus.bounty.review;

import com.campus.bounty.common.BizException;
import com.campus.bounty.trade.OrderStatus;
import com.campus.bounty.trade.TradeOrder;
import com.campus.bounty.trade.TradeOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewService {
    private final ReviewRepository reviewRepository;
    private final TradeOrderRepository orderRepository;

    @Transactional
    public Review review(Long orderId, Long fromUserId, String content) {
        TradeOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BizException("订单不存在"));
        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw new BizException("订单未完成，不能评价");
        }

        Long toUserId;
        if (fromUserId.equals(order.getBuyerId())) {
            toUserId = order.getSellerId();
        } else if (fromUserId.equals(order.getSellerId())) {
            toUserId = order.getBuyerId();
        } else {
            throw new BizException("你不是该订单参与方");
        }
        if (reviewRepository.existsByOrderIdAndFromUserId(orderId, fromUserId)) {
            throw new BizException("你已评价过该订单");
        }
        if (content == null || content.isBlank()) {
            throw new BizException("评价内容不能为空");
        }

        Review review = new Review();
        review.setOrderId(orderId);
        review.setFromUserId(fromUserId);
        review.setToUserId(toUserId);
        review.setContent(content);
        return reviewRepository.save(review);
    }

    public List<Review> ofUser(Long userId) {
        return reviewRepository.findByToUserIdOrderByIdDesc(userId);
    }
}
