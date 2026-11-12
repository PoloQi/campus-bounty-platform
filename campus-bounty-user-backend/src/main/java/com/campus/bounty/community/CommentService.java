package com.campus.bounty.community;

import com.campus.bounty.common.BizException;
import com.campus.bounty.community.dto.CommentReq;
import com.campus.bounty.community.dto.CommentResp;
import com.campus.bounty.user.User;
import com.campus.bounty.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {
    private final PostRepository postRepository;
    private final PostLikeRepository likeRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;

    @Transactional
    public int toggleLike(Long postId, Long userId) {
        Post post = visiblePost(postId);
        Optional<PostLike> existing = likeRepository.findByPostIdAndUserId(postId, userId);
        if (existing.isPresent()) {
            likeRepository.delete(existing.get());
            post.setLikeCount(Math.max(0, post.getLikeCount() - 1));
        } else {
            PostLike like = new PostLike();
            like.setPostId(postId);
            like.setUserId(userId);
            likeRepository.save(like);
            post.setLikeCount(post.getLikeCount() + 1);
        }
        postRepository.save(post);
        return post.getLikeCount();
    }

    @Transactional
    public Comment add(Long postId, Long authorId, CommentReq request) {
        visiblePost(postId);
        if (request.getContent() == null || request.getContent().isBlank()) {
            throw new BizException("评论内容不能为空");
        }
        if (request.getContent().length() > 500) {
            throw new BizException("评论内容不能超过500字");
        }
        if (request.getReplyToId() != null) {
            Comment target = commentRepository.findById(request.getReplyToId())
                    .orElseThrow(() -> new BizException("回复的评论不存在"));
            if (!target.getPostId().equals(postId)) {
                throw new BizException("不能跨帖回复");
            }
        }

        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setAuthorId(authorId);
        comment.setContent(request.getContent());
        comment.setReplyToId(request.getReplyToId());
        return commentRepository.save(comment);
    }

    public List<CommentResp> list(Long postId) {
        visiblePost(postId);
        List<Comment> comments = commentRepository.findByPostIdOrderByIdAsc(postId);
        Map<Long, User> authors = userRepository.findAllById(comments.stream()
                        .map(Comment::getAuthorId).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return comments.stream().map(comment -> {
            User author = authors.get(comment.getAuthorId());
            return new CommentResp(comment.getId(), comment.getPostId(), comment.getAuthorId(),
                    author == null ? "" : author.getNickname(),
                    author == null ? "" : author.getAvatar(),
                    comment.getContent(), comment.getReplyToId(), comment.getCreatedAt());
        }).toList();
    }

    private Post visiblePost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BizException("帖子不存在"));
        if (!"VISIBLE".equals(post.getStatus())) {
            throw new BizException("帖子已删除");
        }
        return post;
    }
}
