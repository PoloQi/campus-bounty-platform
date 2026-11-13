package com.campus.bounty.trade;

import com.campus.bounty.trade.dto.ServicePublishReq;
import com.campus.bounty.user.User;
import com.campus.bounty.user.UserService;
import com.campus.bounty.wallet.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
class ServiceMultiOrderTest {
    @Autowired
    TradePostService postService;
    @Autowired
    TradeOrderService orderService;
    @Autowired
    TradePostRepository postRepository;
    @Autowired
    TradeOrderRepository orderRepository;
    @Autowired
    WalletService walletService;
    @Autowired
    UserService userService;

    @Test
    void service_post_stays_open_after_order_completion_and_next_buyer_can_purchase() {
        User publisher = register("service_multi_confirm_publisher");
        User firstBuyer = register("service_multi_confirm_first");
        User secondBuyer = register("service_multi_confirm_second");
        walletService.recharge(firstBuyer.getId(), new BigDecimal("30.00"));
        walletService.recharge(secondBuyer.getId(), new BigDecimal("30.00"));
        TradePost post = publish(publisher, "20.00");

        TradeOrder first = orderService.buy(post.getId(), firstBuyer.getId());
        orderService.deliver(first.getId(), publisher.getId());
        TradeOrder completed = orderService.confirm(first.getId(), firstBuyer.getId());

        assertEquals(OrderStatus.COMPLETED, completed.getStatus());
        assertEquals(PostStatus.OPEN, postRepository.findById(post.getId()).orElseThrow().getStatus());

        TradeOrder second = orderService.buy(post.getId(), secondBuyer.getId());
        assertEquals(OrderStatus.IN_PROGRESS, second.getStatus());
        assertEquals(PostStatus.OPEN, postRepository.findById(post.getId()).orElseThrow().getStatus());
    }

    @Test
    void cancelling_one_service_order_keeps_post_open_and_other_order_intact() {
        User publisher = register("service_multi_cancel_publisher");
        User firstBuyer = register("service_multi_cancel_first");
        User secondBuyer = register("service_multi_cancel_second");
        walletService.recharge(firstBuyer.getId(), new BigDecimal("30.00"));
        walletService.recharge(secondBuyer.getId(), new BigDecimal("30.00"));
        TradePost post = publish(publisher, "20.00");

        TradeOrder first = orderService.buy(post.getId(), firstBuyer.getId());
        TradeOrder second = orderService.buy(post.getId(), secondBuyer.getId());

        TradeOrder cancelled = orderService.cancel(second.getId(), secondBuyer.getId());

        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
        assertEquals(PostStatus.OPEN, postRepository.findById(post.getId()).orElseThrow().getStatus());
        assertEquals(OrderStatus.IN_PROGRESS,
                orderRepository.findById(first.getId()).orElseThrow().getStatus());
        assertEquals(0, walletService.getOrCreate(firstBuyer.getId()).getFrozen()
                .compareTo(new BigDecimal("20.00")));
    }

    private TradePost publish(User publisher, String price) {
        ServicePublishReq request = new ServicePublishReq();
        request.setTitle("高数一对一辅导");
        request.setDescription("两小时线上辅导");
        request.setPrice(new BigDecimal(price));
        request.setCategory("学习");
        return postService.publishService(publisher.getId(), request);
    }

    private User register(String username) {
        return userService.register(username, "123456", "123456", "服务多单测试");
    }
}
