package com.campus.bounty.admin;

import com.campus.bounty.admin.dto.HandleReportReq;
import com.campus.bounty.common.ApiResponse;
import com.campus.bounty.config.CurrentUser;
import com.campus.bounty.trade.OrderStatus;
import com.campus.bounty.trade.PostType;
import com.campus.bounty.wallet.Wallet;
import com.campus.bounty.wallet.WalletLog;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    private final AdminService adminService;
    private final AdminQueries queries;

    @GetMapping("/dashboard")
    public ApiResponse<AdminQueries.Dashboard> dashboard() {
        return ApiResponse.ok(queries.dashboard());
    }

    @GetMapping("/orders")
    public ApiResponse<AdminQueries.Page<AdminQueries.OrderRow>> orders(
            @RequestParam(required = false) OrderStatus status, @RequestParam(required = false) PostType type,
            @RequestParam(required = false) Long buyerId, @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate through,
            @RequestParam(defaultValue = "0") int page) {
        return ApiResponse.ok(queries.orders(status, type, buyerId, sellerId, from, through, page));
    }

    @GetMapping("/orders/{id}")
    public ApiResponse<AdminQueries.OrderDetail> order(@PathVariable Long id) {
        return ApiResponse.ok(queries.order(id));
    }

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

    @GetMapping("/users")
    public ApiResponse<AdminQueries.Page<AdminQueries.UserRow>> users(
            @RequestParam(required = false) String keyword, @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page) {
        return ApiResponse.ok(queries.users(keyword, status, page));
    }

    @GetMapping("/reports")
    public ApiResponse<List<Report>> reports() {
        return ApiResponse.ok(adminService.pendingReports(CurrentUser.id()));
    }

    @GetMapping("/reports/query")
    public ApiResponse<AdminQueries.Page<AdminQueries.ReportRow>> reportCases(
            @RequestParam(required = false) String status, @RequestParam(required = false) String targetType,
            @RequestParam(defaultValue = "0") int page) {
        return ApiResponse.ok(queries.reports(status, targetType, page));
    }

    @GetMapping("/reports/{id}")
    public ApiResponse<AdminQueries.ReportRow> report(@PathVariable Long id) {
        return ApiResponse.ok(queries.report(id));
    }

    @PostMapping("/reports/{id}/handle")
    public ApiResponse<Void> handleReport(@PathVariable Long id, @Valid @RequestBody HandleReportReq request) {
        adminService.handleReport(CurrentUser.id(), id, request);
        return ApiResponse.ok();
    }

    @GetMapping("/moderation/{type}/{id}")
    public ApiResponse<AdminQueries.ModerationTarget> target(@PathVariable String type, @PathVariable Long id) {
        return ApiResponse.ok(queries.target(type, id));
    }

    @GetMapping("/funds")
    public ApiResponse<AdminQueries.FundTotals> funds() {
        return ApiResponse.ok(queries.funds());
    }

    @GetMapping("/funds/wallets/{userId}")
    public ApiResponse<Wallet> wallet(@PathVariable Long userId) {
        return ApiResponse.ok(queries.wallet(userId));
    }

    @GetMapping("/funds/ledger")
    public ApiResponse<AdminQueries.Page<WalletLog>> ledger(
            @RequestParam(required = false) Long userId, @RequestParam(required = false) String type,
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate through,
            @RequestParam(defaultValue = "0") int page) {
        return ApiResponse.ok(queries.ledger(userId, type, orderId, from, through, page));
    }

    @GetMapping("/audit")
    public ApiResponse<AdminQueries.Page<AdminQueries.AuditRow>> audit(
            @RequestParam(required = false) Long adminId, @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate through,
            @RequestParam(defaultValue = "0") int page) {
        return ApiResponse.ok(queries.audit(adminId, action, from, through, page));
    }
}
