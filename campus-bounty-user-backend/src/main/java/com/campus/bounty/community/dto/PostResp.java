package com.campus.bounty.community.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class PostResp {
    private Long id;
    private Long authorId;
    private String authorNickname;
    private String authorAvatar;
    private String content;
    private String images;
    private String tags;
    private Integer likeCount;
    private Long commentCount;
    private String status;
    private LocalDateTime createdAt;
}
