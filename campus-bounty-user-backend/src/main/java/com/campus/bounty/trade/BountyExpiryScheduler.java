package com.campus.bounty.trade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
class BountyExpiryScheduler {
    private static final PageRequest BATCH = PageRequest.of(0, 100);

    private final TradeOrderRepository orderRepository;
    private final TradeOrderService orderService;

    @Scheduled(initialDelayString = "${app.bounty-expiry-scan-ms:15000}",
            fixedDelayString = "${app.bounty-expiry-scan-ms:15000}")
    void notifyDueBounties() {
        for (Long orderId : orderRepository.findDueBountyNoticeIds(
                LocalDateTime.now(), TradeOrderService.BOUNTY_EXPIRED, BATCH)) {
            try {
                orderService.notifyDeadlineElapsed(orderId);
            } catch (RuntimeException error) {
                log.error("Unable to send deadline notice for order {}", orderId, error);
            }
        }
    }
}
