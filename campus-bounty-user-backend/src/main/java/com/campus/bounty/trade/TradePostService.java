package com.campus.bounty.trade;

import com.campus.bounty.common.BizException;
import com.campus.bounty.trade.dto.PostView;
import com.campus.bounty.trade.dto.PublishReq;
import com.campus.bounty.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TradePostService {
    private final TradePostRepository postRepository;
    private final TradeOrderRepository orderRepository;
    private final WalletService walletService;

    @Transactional
    public TradePost publish(Long publisherId, PublishReq request) {
        TradePost post = new TradePost();
        post.setPublisherId(publisherId);
        post.setType(PostType.BOUNTY);
        post.setTitle(request.getTitle());
        post.setDescription(request.getDescription());
        post.setImages(request.getImages());
        post.setPrice(request.getPrice());
        post.setCategory(request.getCategory());
        post.setDeadline(request.getDeadline());
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

    public List<TradePost> hall(PostType type, String category, String keyword) {
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword;
        return postRepository.search(type, category, normalizedKeyword);
    }

    public PostView detail(Long id) {
        TradePost post = postRepository.findById(id)
                .orElseThrow(() -> new BizException("悬赏不存在"));
        TradeOrder order = orderRepository.findByPostId(id).orElse(null);
        return new PostView(post, order);
    }

    public List<TradePost> hallMine(Long publisherId) {
        return postRepository.findByPublisherIdOrderByIdDesc(publisherId);
    }
}
