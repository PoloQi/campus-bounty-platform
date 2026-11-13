package com.campus.bounty.community.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class CommentResp {
    private Long id;
    private Long postId;
    private Long authorId;
    private String authorNickname;
    private String authorAvatar;
    private String content;
    private Long replyToId;
    private LocalDateTime createdAt;
}
