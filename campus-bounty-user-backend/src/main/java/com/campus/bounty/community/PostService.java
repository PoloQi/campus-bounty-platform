package com.campus.bounty.community;

import com.campus.bounty.common.BizException;
import com.campus.bounty.community.dto.CreatePostReq;
import com.campus.bounty.community.dto.PostResp;
import com.campus.bounty.community.dto.UpdatePostReq;
import com.campus.bounty.user.User;
import com.campus.bounty.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostService {
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;

    @Transactional
    public Post create(Long authorId, CreatePostReq request) {
        Post post = new Post();
        post.setAuthorId(authorId);
        post.setContent(request.getContent());
        post.setImages(request.getImages());
        post.setTags(request.getTags());
        return postRepository.save(post);
    }

    public List<PostResp> feed(String tag) {
        List<Post> posts = tag == null || tag.isBlank()
                ? postRepository.findByStatusOrderByIdDesc("VISIBLE")
                : postRepository.findByStatusAndTagsContainingOrderByIdDesc("VISIBLE", tag);
        return toResponses(posts);
    }

    public List<PostResp> mine(Long authorId) {
        return toResponses(postRepository.findByAuthorIdOrderByIdDesc(authorId));
    }

    @Transactional
    public PostResp update(Long postId, Long authorId, UpdatePostReq request) {
        Post post = visibleOwnedPost(postId, authorId);
        post.setContent(request.getContent());
        post.setImages(request.getImages());
        post.setTags(request.getTags());
        return toResponses(List.of(postRepository.save(post))).get(0);
    }

    @Transactional
    public void delete(Long postId, Long authorId) {
        Post post = visibleOwnedPost(postId, authorId);
        post.setStatus("DELETED");
        postRepository.save(post);
    }

    private Post visibleOwnedPost(Long postId, Long authorId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BizException("帖子不存在"));
        if (!post.getAuthorId().equals(authorId)) {
            throw new BizException("只有作者可以操作该帖子");
        }
        if (!"VISIBLE".equals(post.getStatus())) {
            throw new BizException("帖子已删除");
        }
        return post;
    }

    private List<PostResp> toResponses(List<Post> posts) {
        Map<Long, User> authors = userRepository.findAllById(posts.stream()
                        .map(Post::getAuthorId).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return posts.stream().map(post -> {
            User author = authors.get(post.getAuthorId());
            return new PostResp(post.getId(), post.getAuthorId(),
                    author == null ? "" : author.getNickname(),
                    author == null ? "" : author.getAvatar(),
                    post.getContent(), post.getImages(), post.getTags(), post.getLikeCount(),
                    commentRepository.countByPostId(post.getId()), post.getStatus(), post.getCreatedAt());
        }).toList();
    }
}
