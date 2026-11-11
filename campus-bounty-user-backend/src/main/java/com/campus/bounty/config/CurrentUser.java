package com.campus.bounty.config;

import com.campus.bounty.common.BizException;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {
    private CurrentUser() {
    }

    public static Long id() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BizException(40100, "未登录");
        }
        return userId;
    }
}
