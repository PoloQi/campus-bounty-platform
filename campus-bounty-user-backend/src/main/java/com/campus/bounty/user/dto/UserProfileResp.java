package com.campus.bounty.user.dto;

import com.campus.bounty.user.User;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class UserProfileResp {
    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String bio;
    private String contact;
    private String role;
    private LocalDateTime createdAt;

    public static UserProfileResp from(User user) {
        return new UserProfileResp(user.getId(), user.getUsername(), user.getNickname(),
                user.getAvatar(), user.getBio(), user.getContact(), user.getRole(), user.getCreatedAt());
    }
}
