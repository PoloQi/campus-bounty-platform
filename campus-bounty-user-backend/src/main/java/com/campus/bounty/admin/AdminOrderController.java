package com.campus.bounty.admin;

import com.campus.bounty.admin.dto.AdminOrderDetailView;
import com.campus.bounty.common.ApiResponse;
import com.campus.bounty.config.CurrentUser;
import com.campus.bounty.trade.OrderStatus;
import com.campus.bounty.trade.PostType;
import com.campus.bounty.trade.TradeOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminOrderController {
    private final AdminQueryService queryService;

    @GetMapping("/orders")
    public ApiResponse<List<TradeOrder>> orders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) PostType postType,
            @RequestParam(required = false) Long buyerId,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ApiResponse.ok(queryService.listOrders(
                CurrentUser.id(), status, postType, buyerId, sellerId, from, to));
    }

    @GetMapping("/orders/{id}")
    public ApiResponse<AdminOrderDetailView> detail(@PathVariable Long id) {
        return ApiResponse.ok(queryService.orderDetail(CurrentUser.id(), id));
    }
}
