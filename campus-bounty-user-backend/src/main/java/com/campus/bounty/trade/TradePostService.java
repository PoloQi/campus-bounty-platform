package com.campus.bounty.trade;

import com.campus.bounty.common.BizException;
import com.campus.bounty.trade.dto.PostView;
import com.campus.bounty.trade.dto.PublishReq;
import com.campus.bounty.trade.dto.ServicePublishReq;
import com.campus.bounty.user.UserRepository;
import com.campus.bounty.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TradePostService {
    private final TradePostRepository postRepository;
    private final TradeOrderRepository orderRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TradePost publish(Long publisherId, PublishReq request) {
        return publish(publisherId, request, UUID.randomUUID());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TradePost publish(Long publisherId, PublishReq request, UUID requestId) {
        // A publisher row exists before the first attempt and serializes concurrent retries.
        userRepository.findByIdForUpdate(publisherId).orElseThrow(() -> new BizException("用户不存在"));
        String publishRequestId = requestId.toString();
        var existing = postRepository.findByPublisherIdAndPublishRequestId(publisherId, publishRequestId);
        if (existing.isPresent()) {
            return existing.get();
        }
        LocalDateTime deadline = request.getDeadline();
        if (deadline == null || !deadline.isAfter(BountyDeadline.now())) {
            throw new BizException("接单截止时间必须晚于当前时间");
        }
        TradePost post = new TradePost();
        post.setPublisherId(publisherId);
        post.setPublishRequestId(publishRequestId);
        post.setType(PostType.BOUNTY);
        post.setTitle(request.getTitle());
        post.setDescription(request.getDescription());
        post.setImages(request.getImages());
        post.setPrice(request.getPrice());
        post.setCategory(request.getCategory());
        post.setDeadline(deadline);
        postRepository.save(post);

        TradeOrder order = new TradeOrder();
        order.setPostId(post.getId());
        order.setBuyerId(publisherId);
        order.setAmount(request.getPrice());
        order.setStatus(OrderStatus.PENDING);
        orderRepository.save(order);

        walletService.freeze(publisherId, request.getPrice(), order.getId());
        return post;
    }

    @Transactional
    public TradePost publishService(Long publisherId, ServicePublishReq request) {
        TradePost post = new TradePost();
        post.setPublisherId(publisherId);
        post.setType(PostType.SERVICE);
        post.setTitle(request.getTitle());
        post.setDescription(request.getDescription());
        post.setImages(request.getImages());
        post.setPrice(request.getPrice());
        post.setCategory(request.getCategory());
        post.setStatus(PostStatus.OPEN);
        return postRepository.save(post);
    }

    public List<TradePost> hall(PostType type, String category, String keyword) {
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword;
        return postRepository.search(type, category, normalizedKeyword);
    }

    public PostView detail(Long id) {
        TradePost post = postRepository.findById(id)
                .orElseThrow(() -> new BizException("悬赏不存在"));
        TradeOrder order = post.getType() == PostType.BOUNTY
                ? orderRepository.findByPostId(id).orElse(null)
                : null;
        return new PostView(post, order);
    }

    public List<TradePost> hallMine(Long publisherId) {
        return postRepository.findByPublisherIdOrderByIdDesc(publisherId);
    }
}
