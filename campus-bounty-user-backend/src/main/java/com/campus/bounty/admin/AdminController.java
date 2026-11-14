package com.campus.bounty.admin;

import com.campus.bounty.common.ApiResponse;
import com.campus.bounty.config.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    @GetMapping("/reports")
    public ApiResponse<List<Report>> reports() {
        return ApiResponse.ok(adminService.pendingReports(CurrentUser.id()));
    }
}
