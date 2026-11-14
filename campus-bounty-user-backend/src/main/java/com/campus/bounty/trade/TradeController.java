package com.campus.bounty.trade;

import com.campus.bounty.common.ApiResponse;
import com.campus.bounty.config.CurrentUser;
import com.campus.bounty.trade.dto.OrderDetailView;
import com.campus.bounty.trade.dto.PostView;
import com.campus.bounty.trade.dto.PublishReq;
import com.campus.bounty.trade.dto.ServicePublishReq;
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
import java.util.Map;

@RestController
@RequestMapping("/api/trade")
@RequiredArgsConstructor
public class TradeController {
    private final TradePostService postService;
    private final TradeOrderService orderService;
    private final AppealService appealService;

    @PostMapping("/posts")
    public ApiResponse<Long> publish(@Valid @RequestBody PublishReq request) {
        return ApiResponse.ok(postService.publish(CurrentUser.id(), request).getId());
    }

    @PostMapping("/services")
    public ApiResponse<Long> publishService(@Valid @RequestBody ServicePublishReq request) {
        return ApiResponse.ok(postService.publishService(CurrentUser.id(), request).getId());
    }

    @GetMapping("/posts")
    public ApiResponse<List<TradePost>> hall(
            @RequestParam(required = false) PostType type,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(postService.hall(type, category, keyword));
    }

    @GetMapping("/posts/{id}")
    public ApiResponse<PostView> detail(@PathVariable Long id) {
        return ApiResponse.ok(postService.detail(id));
    }

    @PostMapping("/posts/{postId}/buy")
    public ApiResponse<TradeOrder> buy(@PathVariable Long postId) {
        return ApiResponse.ok(orderService.buy(postId, CurrentUser.id()));
    }

    @GetMapping("/orders/{id}")
    public ApiResponse<OrderDetailView> orderDetail(@PathVariable Long id) {
        return ApiResponse.ok(orderService.detailForParticipant(id, CurrentUser.id()));
    }

    @PostMapping("/orders/{id}/accept")
    public ApiResponse<TradeOrder> accept(@PathVariable Long id) {
        return ApiResponse.ok(orderService.accept(id, CurrentUser.id()));
    }

    @PostMapping("/orders/{id}/deliver")
    public ApiResponse<TradeOrder> deliver(@PathVariable Long id) {
        return ApiResponse.ok(orderService.deliver(id, CurrentUser.id()));
    }

    @PostMapping("/orders/{id}/confirm")
    public ApiResponse<TradeOrder> confirm(@PathVariable Long id) {
        return ApiResponse.ok(orderService.confirm(id, CurrentUser.id()));
    }

    @PostMapping("/orders/{id}/cancel")
    public ApiResponse<TradeOrder> cancel(@PathVariable Long id) {
        return ApiResponse.ok(orderService.cancel(id, CurrentUser.id()));
    }

    @PostMapping("/orders/{id}/appeal")
    public ApiResponse<Appeal> appeal(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(appealService.open(id, CurrentUser.id(), String.valueOf(body.get("reason"))));
    }

    @PostMapping("/appeals/{id}/withdraw")
    public ApiResponse<Appeal> withdrawAppeal(@PathVariable Long id) {
        return ApiResponse.ok(appealService.withdraw(id, CurrentUser.id()));
    }

    @GetMapping("/orders/{id}/appeal")
    public ApiResponse<Appeal> openAppeal(@PathVariable Long id) {
        return ApiResponse.ok(appealService.viewOpen(id, CurrentUser.id()));
    }

    @GetMapping("/orders/mine")
    public ApiResponse<Map<String, Object>> mineOrders() {
        Long userId = CurrentUser.id();
        return ApiResponse.ok(Map.of(
                "asBuyer", orderService.ordersAsBuyer(userId),
                "asSeller", orderService.ordersAsSeller(userId),
                "published", postService.hallMine(userId)));
    }
}
