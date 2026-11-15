package com.campus.bounty.admin;

import com.campus.bounty.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminAuditController {
    private final AuditLogService auditLogService;

    @GetMapping("/logs")
    public ApiResponse<List<AdminLog>> logs(
            @RequestParam(required = false) Long adminId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ApiResponse.ok(auditLogService.query(adminId, blankToNull(action), from, to));
    }

    /** 前端拼 query 时容易把空串传下来，后端兜一层，避免 action='' 被当合法值筛成空列表。 */
    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
