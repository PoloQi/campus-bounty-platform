package com.campus.bounty.admin;

import com.campus.bounty.admin.dto.AdminAppealReq;
import com.campus.bounty.admin.dto.AppealDetailView;
import com.campus.bounty.admin.dto.AppealListItem;
import com.campus.bounty.common.ApiResponse;
import com.campus.bounty.config.CurrentUser;
import com.campus.bounty.trade.Appeal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端申诉仲裁入口。
 * <p>
 * 两个裁决动作走两个独立路径（而不是 body 里的 action 字符串）：动作语义写在 URL 上，
 * 前端点错按钮的代价更小，也不必再写一层枚举白名单校验。
 */
@RestController
@RequestMapping("/api/admin/appeals")
@RequiredArgsConstructor
public class AdminAppealController {
    private final AdminAppealService adminAppealService;

    @GetMapping
    public ApiResponse<List<AppealListItem>> list(@RequestParam(required = false) String status) {
        return ApiResponse.ok(adminAppealService.list(CurrentUser.id(), status));
    }

    @GetMapping("/{id}")
    public ApiResponse<AppealDetailView> detail(@PathVariable Long id) {
        return ApiResponse.ok(adminAppealService.detail(CurrentUser.id(), id));
    }

    @PostMapping("/{id}/release")
    public ApiResponse<Appeal> release(@PathVariable Long id,
                                       @Valid @RequestBody AdminAppealReq request) {
        return ApiResponse.ok(adminAppealService.release(CurrentUser.id(), id, request.getComment()));
    }

    @PostMapping("/{id}/refund")
    public ApiResponse<Appeal> refund(@PathVariable Long id,
                                      @Valid @RequestBody AdminAppealReq request) {
        return ApiResponse.ok(adminAppealService.refund(CurrentUser.id(), id, request.getComment()));
    }
}
