package com.campus.bounty.community;

import com.campus.bounty.common.BizException;
import com.campus.bounty.community.dto.CommentReq;
import com.campus.bounty.community.dto.CommentResp;
import com.campus.bounty.community.dto.CreatePostReq;
import com.campus.bounty.community.dto.PostResp;
import com.campus.bounty.user.User;
import com.campus.bounty.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class LikeCommentTest {
    @Autowired
    CommentService commentService;
    @Autowired
    PostService postService;
    @Autowired
    PostRepository postRepository;
    @Autowired
    PostLikeRepository likeRepository;
    @Autowired
    UserService userService;

    @Test
    void like_toggles_without_duplicate_rows() {
        User author = register("like_toggle_author");
        User reader = register("like_toggle_reader");
        Post post = createPost(author, "点赞测试");

        assertEquals(1, commentService.toggleLike(post.getId(), reader.getId()));
        assertEquals(1, likeRepository.countByPostId(post.getId()));
        assertEquals(1, postRepository.findById(post.getId()).orElseThrow().getLikeCount());

        assertEquals(0, commentService.toggleLike(post.getId(), reader.getId()));
        assertEquals(0, likeRepository.countByPostId(post.getId()));
        assertEquals(0, postRepository.findById(post.getId()).orElseThrow().getLikeCount());
    }

    @Test
    void deleted_post_cannot_be_liked() {
        User author = register("like_deleted_author");
        User reader = register("like_deleted_reader");
        Post post = createPost(author, "删除后点赞");
        postService.delete(post.getId(), author.getId());

        assertThrows(BizException.class, () -> commentService.toggleLike(post.getId(), reader.getId()));
    }

    @Test
    void comments_are_returned_in_creation_order_with_author_names() {
        User author = register("comment_order_author");
        User firstReader = register("comment_order_first");
        User secondReader = register("comment_order_second");
        Post post = createPost(author, "评论测试");

        Comment first = commentService.add(post.getId(), firstReader.getId(), commentReq("第一条", null));
        Comment second = commentService.add(post.getId(), secondReader.getId(), commentReq("第二条", null));
        List<CommentResp> comments = commentService.list(post.getId());

        assertEquals(List.of(first.getId(), second.getId()), comments.stream().map(CommentResp::getId).toList());
        assertEquals(firstReader.getNickname(), comments.get(0).getAuthorNickname());
        assertEquals(secondReader.getNickname(), comments.get(1).getAuthorNickname());
    }

    @Test
    void reply_must_reference_comment_in_same_post() {
        User author = register("comment_reply_author");
        Post firstPost = createPost(author, "帖子一");
        Post secondPost = createPost(author, "帖子二");
        Comment target = commentService.add(firstPost.getId(), author.getId(), commentReq("目标评论", null));

        Comment reply = commentService.add(firstPost.getId(), author.getId(),
                commentReq("正常回复", target.getId()));

        assertEquals(target.getId(), reply.getReplyToId());
        assertThrows(BizException.class, () -> commentService.add(secondPost.getId(), author.getId(),
                commentReq("跨帖回复", target.getId())));
    }

    @Test
    void deleted_post_and_empty_content_cannot_be_commented() {
        User author = register("comment_invalid_author");
        Post post = createPost(author, "无效评论");

        assertThrows(BizException.class, () -> commentService.add(post.getId(), author.getId(),
                commentReq("  ", null)));

        postService.delete(post.getId(), author.getId());
        assertThrows(BizException.class, () -> commentService.add(post.getId(), author.getId(),
                commentReq("删除后评论", null)));
    }

    @Test
    void feed_contains_actual_comment_count() {
        User author = register("comment_count_author");
        Post post = createPost(author, "评论计数");
        commentService.add(post.getId(), author.getId(), commentReq("第一条", null));
        commentService.add(post.getId(), author.getId(), commentReq("第二条", null));

        PostResp response = postService.feed(null).stream()
                .filter(item -> item.getId().equals(post.getId())).findFirst().orElseThrow();

        assertEquals(2, response.getCommentCount());
        assertTrue(commentService.list(post.getId()).size() == response.getCommentCount());
    }

    private Post createPost(User author, String content) {
        CreatePostReq request = new CreatePostReq();
        request.setContent(content);
        return postService.create(author.getId(), request);
    }

    private CommentReq commentReq(String content, Long replyToId) {
        CommentReq request = new CommentReq();
        request.setContent(content);
        request.setReplyToId(replyToId);
        return request;
    }

    private User register(String username) {
        return userService.register(username, "123456", "123456", "评论用户");
    }
}
