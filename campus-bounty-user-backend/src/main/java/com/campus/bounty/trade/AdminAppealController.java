package com.campus.bounty.trade;

import com.campus.bounty.admin.AdminService;
import com.campus.bounty.common.ApiResponse;
import com.campus.bounty.common.BizException;
import com.campus.bounty.config.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/appeals")
@RequiredArgsConstructor
public class AdminAppealController {
    private final AdminService adminService;
    private final AppealRepository appealRepository;
    private final AppealService appealService;

    @GetMapping
    public ApiResponse<AppealPage> list(@RequestParam(required = false) String status,
                                       @RequestParam(defaultValue = "0") int page) {
        adminService.requireAdmin(CurrentUser.id());
        if (page < 0 || (status != null && !Set.of("OPEN", "RESOLVED", "WITHDRAWN").contains(status))) {
            throw new BizException("申诉筛选条件无效");
        }
        Page<Appeal> results = appealRepository.listForAdmin(status, PageRequest.of(page, 20));
        return ApiResponse.ok(new AppealPage(results.getContent(), page, results.getTotalPages()));
    }

    @GetMapping("/{id}")
    public ApiResponse<Appeal> detail(@PathVariable Long id) {
        adminService.requireAdmin(CurrentUser.id());
        return ApiResponse.ok(appealRepository.findById(id).orElseThrow(() -> new BizException("申诉不存在")));
    }

    @PostMapping("/{id}/arbitrate")
    public ApiResponse<Appeal> arbitrate(@PathVariable Long id, @RequestBody ArbitrationRequest request) {
        return ApiResponse.ok(appealService.arbitrate(id, CurrentUser.id(), request.outcome(), request.comment()));
    }

    record AppealPage(List<Appeal> items, int page, int totalPages) {}

    record ArbitrationRequest(AppealOutcome outcome, String comment) {}
}
