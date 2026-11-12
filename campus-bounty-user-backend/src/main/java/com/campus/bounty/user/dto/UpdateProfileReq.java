package com.campus.bounty.user.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileReq {
    @Size(max = 30, message = "昵称长度不能超过30")
    private String nickname;

    @Size(max = 200, message = "头像地址长度不能超过200")
    private String avatar;

    @Size(max = 200, message = "个人简介长度不能超过200")
    private String bio;

    @Size(max = 60, message = "联系方式长度不能超过60")
    private String contact;
}
