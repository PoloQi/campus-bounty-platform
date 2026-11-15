package com.campus.bounty.admin;

import com.campus.bounty.admin.dto.DashboardView;
import com.campus.bounty.common.ApiResponse;
import com.campus.bounty.config.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端数据看板入口（设计 §8）。
 * <p>
 * 只有一个端点，且刻意不提供 {@code ?from=&to=} 之类的自定义区间：
 * 看板是「一屏运营概览」，口径固定成 7 天才好横向比较；需要任意区间请走
 * {@code /api/admin/orders} 的筛选。多一个参数就多一套缓存与口径解释成本。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminDashboardController {
    private final AdminDashboardService adminDashboardService;

    @GetMapping("/dashboard")
    public ApiResponse<DashboardView> dashboard() {
        return ApiResponse.ok(adminDashboardService.overview(CurrentUser.id()));
    }
}
