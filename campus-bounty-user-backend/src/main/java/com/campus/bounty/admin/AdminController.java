package com.campus.bounty.admin;

import com.campus.bounty.common.ApiResponse;
import com.campus.bounty.config.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    private final AdminService adminService;

    @PostMapping("/posts/{id}/delete")
    public ApiResponse<Void> deletePost(@PathVariable Long id) {
        adminService.deletePost(CurrentUser.id(), id);
        return ApiResponse.ok();
    }

    @PostMapping("/services/{id}/close")
    public ApiResponse<Void> closePost(@PathVariable Long id) {
        adminService.closeTradePost(CurrentUser.id(), id);
        return ApiResponse.ok();
    }

    @PostMapping("/users/{id}/ban")
    public ApiResponse<Void> ban(@PathVariable Long id) {
        adminService.banUser(CurrentUser.id(), id);
        return ApiResponse.ok();
    }

    @PostMapping("/users/{id}/unban")
    public ApiResponse<Void> unban(@PathVariable Long id) {
        adminService.unbanUser(CurrentUser.id(), id);
        return ApiResponse.ok();
    }

    // GET /api/admin/reports 已迁到 AdminReportController（B4 起支持 status/targetType 筛选），
    // 路径与鉴权行为不变，避免两个控制器映射同一路径导致启动报 Ambiguous mapping。
}
