package com.campus.bounty.wallet;

import com.campus.bounty.common.ApiResponse;
import com.campus.bounty.config.CurrentUser;
import com.campus.bounty.wallet.dto.RechargeReq;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {
    private final WalletService walletService;

    @GetMapping
    public ApiResponse<Wallet> mine() {
        return ApiResponse.ok(walletService.getOrCreate(CurrentUser.id()));
    }

    @PostMapping("/recharge")
    public ApiResponse<Wallet> recharge(@Valid @RequestBody RechargeReq request) {
        return ApiResponse.ok(walletService.recharge(CurrentUser.id(), request.getAmount()));
    }

    @GetMapping("/logs")
    public ApiResponse<List<WalletLog>> logs() {
        return ApiResponse.ok(walletService.logs(CurrentUser.id()));
    }
}
