package com.campus.bounty.admin.dto;

import com.campus.bounty.user.User;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端看到的用户摘要。
 * <p>
 * 刻意用「白名单式」逐字段构造，而不是直接返回 {@link User} 实体或按黑名单排除。
 * 理由：{@code passwordHash} 一旦被序列化出去就是不可挽回的泄露；白名单在实体新增敏感字段时
 * 默认不会外泄，黑名单则必须每次都记得改。
 */
@Data
@AllArgsConstructor
public class UserBrief {
    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    /** 联系方式：管理员处理纠纷时需要联系当事双方，故保留；不属于凭据类信息。 */
    private String contact;
    private String role;
    private String status;
    private LocalDateTime createdAt;

    public static UserBrief of(User user) {
        if (user == null) {
            return null;
        }
        return new UserBrief(user.getId(), user.getUsername(), user.getNickname(), user.getAvatar(),
                user.getContact(), user.getRole(), user.getStatus(), user.getCreatedAt());
    }
}
