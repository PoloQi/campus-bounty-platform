package com.campus.bounty.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResp {
    private String token;
    private Long userId;
    private String nickname;
    private String role;
}
