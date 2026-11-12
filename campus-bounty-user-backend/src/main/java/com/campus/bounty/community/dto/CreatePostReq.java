package com.campus.bounty.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreatePostReq {
    @NotBlank(message = "帖子内容不能为空")
    @Size(max = 2000, message = "帖子内容不能超过2000字")
    private String content;

    @Size(max = 1000, message = "图片地址不能超过1000字")
    private String images;

    @Size(max = 200, message = "标签不能超过200字")
    private String tags;
}
