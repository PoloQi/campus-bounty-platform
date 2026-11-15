package com.campus.bounty.admin;

import com.campus.bounty.admin.dto.FrozenSummaryView;
import com.campus.bounty.admin.dto.UserBrief;
import com.campus.bounty.common.ApiResponse;
import com.campus.bounty.config.CurrentUser;
import com.campus.bounty.wallet.Wallet;
import com.campus.bounty.wallet.WalletLog;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端用户与资金只读接口（B3）。
 * <p>
 * 刻意不提供任何「充值和扣减、改余额、补流水」的写入口——设计 §2 明确列为非目标。
 * 管理员想影响资金，唯一合法的路径是申诉仲裁。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminFundController {
    private final AdminQueryService queryService;

    @GetMapping("/users")
    public ApiResponse<List<UserBrief>> users(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ApiResponse.ok(queryService.listUsers(
                CurrentUser.id(), keyword, role, status, from, to));
    }

    @GetMapping("/users/{id}/wallet")
    public ApiResponse<Wallet> userWallet(@PathVariable Long id) {
        return ApiResponse.ok(queryService.userWallet(CurrentUser.id(), id));
    }

    @GetMapping("/users/{id}/wallet-logs")
    public ApiResponse<List<WalletLog>> userWalletLogs(@PathVariable Long id) {
        return ApiResponse.ok(queryService.userWalletLogs(CurrentUser.id(), id));
    }

    @GetMapping("/wallet-logs")
    public ApiResponse<List<WalletLog>> walletLogs(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ApiResponse.ok(queryService.platformWalletLogs(
                CurrentUser.id(), userId, type, from, to));
    }

    @GetMapping("/wallets/frozen-summary")
    public ApiResponse<FrozenSummaryView> frozenSummary() {
        return ApiResponse.ok(queryService.frozenSummary(CurrentUser.id()));
    }
}
