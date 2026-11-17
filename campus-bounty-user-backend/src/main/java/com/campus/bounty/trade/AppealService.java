package com.campus.bounty.trade;

import com.campus.bounty.common.BizException;
import com.campus.bounty.admin.AdminAuditService;
import com.campus.bounty.admin.AdminService;
import com.campus.bounty.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AppealService {
    private final AppealRepository appealRepository;
    private final TradeOrderRepository orderRepository;
    private final NotificationService notificationService;
    private final TradeOrderService orderService;
    private final AdminService adminService;
    private final AdminAuditService adminAuditService;

    @Transactional
    public Appeal open(Long orderId, Long appellantId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BizException("申诉理由不能为空");
        }
        if (reason.length() > 500) {
            throw new BizException("申诉理由不超过 500 字");
        }
        TradeOrder order = lockOrder(orderId);
        if (order.getStatus() != OrderStatus.IN_PROGRESS && order.getStatus() != OrderStatus.CONFIRMING) {
            throw new BizException("当前订单状态不允许申诉");
        }
        if (!appellantId.equals(order.getBuyerId()) && !appellantId.equals(order.getSellerId())) {
            throw new BizException("只有订单交易双方可以申诉");
        }
        if (appealRepository.findFirstByOrderIdAndStatusOrderByIdDesc(orderId, "OPEN").isPresent()) {
            throw new BizException("该订单已有进行中的申诉");
        }

        Appeal appeal = new Appeal();
        appeal.setOrderId(orderId);
        appeal.setAppellantId(appellantId);
        appeal.setReason(reason);
        appeal.setFromStatus(order.getStatus().name());
        appealRepository.save(appeal);

        order.setStatus(OrderStatus.DISPUTED);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        notificationService.push(otherParty(order, appellantId), "APPEAL_CREATED",
                "对方已就订单 #" + orderId + " 发起申诉，订单暂停处理", orderId);
        return appeal;
    }

    @Transactional
    public Appeal withdraw(Long appealId, Long operatorId) {
        TradeOrder order = lockAppealOrder(appealId);
        Appeal appeal = lockOpenAppeal(appealId, order);
        if (!appeal.getAppellantId().equals(operatorId)) {
            throw new BizException("只能撤回自己的申诉");
        }

        order.setStatus(OrderStatus.valueOf(appeal.getFromStatus()));
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        appeal.setStatus("WITHDRAWN");
        appeal.setHandledAt(LocalDateTime.now());
        appealRepository.save(appeal);

        notificationService.push(otherParty(order, operatorId), "APPEAL_WITHDRAWN",
                "对方已撤回订单 #" + order.getId() + " 的申诉", order.getId());
        return appeal;
    }

    @Transactional
    public Appeal arbitrate(Long appealId, Long adminId, AppealOutcome outcome, String comment) {
        adminService.requireAdmin(adminId);
        if (outcome == null || comment == null || comment.isBlank() || comment.length() > 500) {
            throw new BizException("请选择裁决结果并填写 1 至 500 字处理意见");
        }
        TradeOrder order = lockAppealOrder(appealId);
        Appeal appeal = lockOpenAppeal(appealId, order);
        boolean release = outcome == AppealOutcome.RELEASE_TO_SELLER;
        orderService.completeEscrow(order, release ? OrderStatus.COMPLETED : OrderStatus.CANCELLED);

        appeal.setStatus("RESOLVED");
        appeal.setOutcome(outcome.name());
        appeal.setAdminId(adminId);
        appeal.setAdminComment(comment.strip());
        appeal.setHandledAt(order.getUpdatedAt());

        String summary = "订单 #" + order.getId() + " 已裁决，"
                + (release ? "托管款已结算给卖家" : "托管款已退回买家");
        notificationService.push(order.getBuyerId(), "APPEAL_RESOLVED", summary, order.getId());
        notificationService.push(order.getSellerId(), "APPEAL_RESOLVED", summary, order.getId());
        adminAuditService.record(adminId, "ARBITRATE_APPEAL", "APPEAL", appealId,
                "订单 #" + order.getId() + " · " + outcome.name());
        return appeal;
    }

    public Appeal viewLatest(Long orderId, Long viewerId) {
        TradeOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BizException("订单不存在"));
        if (!viewerId.equals(order.getBuyerId()) && !viewerId.equals(order.getSellerId())) {
            throw new BizException("无权查看该订单申诉");
        }
        return appealRepository.findFirstByOrderIdOrderByIdDesc(orderId).orElse(null);
    }

    private TradeOrder lockAppealOrder(Long appealId) {
        Long orderId = appealRepository.findOrderId(appealId)
                .orElseThrow(() -> new BizException("申诉不存在"));
        return lockOrder(orderId);
    }

    private TradeOrder lockOrder(Long orderId) {
        return orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BizException("订单不存在"));
    }

    private Appeal lockOpenAppeal(Long appealId, TradeOrder order) {
        // All appeal writes lock the order first, then re-read the appeal under lock.
        Appeal appeal = appealRepository.findByIdForUpdate(appealId)
                .orElseThrow(() -> new BizException("申诉不存在"));
        if (!"OPEN".equals(appeal.getStatus())) {
            throw new BizException("申诉已结束");
        }
        if (order.getStatus() != OrderStatus.DISPUTED) {
            throw new BizException("订单当前不在纠纷中");
        }
        return appeal;
    }

    private Long otherParty(TradeOrder order, Long me) {
        return me.equals(order.getBuyerId()) ? order.getSellerId() : order.getBuyerId();
    }
}
