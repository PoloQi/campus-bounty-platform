package com.campus.bounty.user.dto;

import com.campus.bounty.user.User;

public record UserSummaryResp(Long id, String nickname, String avatar, String bio) {
    public static UserSummaryResp from(User user) {
        return new UserSummaryResp(user.getId(), user.getNickname(), user.getAvatar(), user.getBio());
    }
}
