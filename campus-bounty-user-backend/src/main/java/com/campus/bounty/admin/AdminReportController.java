package com.campus.bounty.admin;

import com.campus.bounty.admin.dto.HandleReportReq;
import com.campus.bounty.common.ApiResponse;
import com.campus.bounty.config.CurrentUser;
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

@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {
    private final AdminReportService adminReportService;

    @GetMapping
    public ApiResponse<List<Report>> list(@RequestParam(required = false) String status,
                                          @RequestParam(required = false) String targetType) {
        return ApiResponse.ok(adminReportService.list(CurrentUser.id(), status, targetType));
    }

    @PostMapping("/{id}/handle")
    public ApiResponse<Report> handle(@PathVariable Long id,
                                      @Valid @RequestBody HandleReportReq request) {
        return ApiResponse.ok(adminReportService.handle(CurrentUser.id(), id, request));
    }
}
