package com.campus.bounty.admin;

import com.campus.bounty.admin.dto.ReportReq;
import com.campus.bounty.common.ApiResponse;
import com.campus.bounty.config.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {
    private final AdminService adminService;

    @PostMapping
    public ApiResponse<Void> report(@Valid @RequestBody ReportReq request) {
        adminService.report(CurrentUser.id(), request.getTargetType(),
                request.getTargetId(), request.getReason());
        return ApiResponse.ok();
    }
}
