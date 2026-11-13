package com.campus.bounty.search;

import com.campus.bounty.community.Post;
import com.campus.bounty.community.PostRepository;
import com.campus.bounty.trade.TradePost;
import com.campus.bounty.trade.TradePostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SearchService {
    private final PostRepository postRepository;
    private final TradePostRepository tradePostRepository;

    public Map<String, Object> search(String keyword) {
        String normalized = keyword == null || keyword.isBlank() ? "" : keyword;
        List<Post> posts = postRepository.findByStatusOrderByIdDesc("VISIBLE").stream()
                .filter(post -> post.getContent() != null && post.getContent().contains(normalized))
                .toList();
        List<TradePost> tradePosts = tradePostRepository.search(null, null,
                normalized.isEmpty() ? null : normalized);
        return Map.of("posts", posts, "tradePosts", tradePosts);
    }
}
