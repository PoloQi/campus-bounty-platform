package com.campus.bounty.trade;

import com.campus.bounty.common.BizException;
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

    @Transactional
    public Appeal open(Long orderId, Long appellantId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BizException("申诉理由不能为空");
        }
        if (reason.length() > 500) {
            throw new BizException("申诉理由不超过 500 字");
        }
        TradeOrder order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BizException("订单不存在"));
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
        Appeal appeal = appealRepository.findById(appealId)
                .orElseThrow(() -> new BizException("申诉不存在"));
        if (!"OPEN".equals(appeal.getStatus())) {
            throw new BizException("申诉已结束，不能撤回");
        }
        if (!appeal.getAppellantId().equals(operatorId)) {
            throw new BizException("只能撤回自己的申诉");
        }
        TradeOrder order = orderRepository.findByIdForUpdate(appeal.getOrderId())
                .orElseThrow(() -> new BizException("订单不存在"));
        if (order.getStatus() != OrderStatus.DISPUTED) {
            throw new BizException("订单当前不在纠纷中");
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

    /** 学生端订单详情查询本订单 OPEN 申诉；非交易双方拒绝；无 OPEN 返回 null。 */
    public Appeal viewOpen(Long orderId, Long viewerId) {
        TradeOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BizException("订单不存在"));
        if (!viewerId.equals(order.getBuyerId()) && !viewerId.equals(order.getSellerId())) {
            throw new BizException("无权查看该订单申诉");
        }
        return appealRepository.findFirstByOrderIdAndStatusOrderByIdDesc(orderId, "OPEN").orElse(null);
    }

    private Long otherParty(TradeOrder order, Long me) {
        return me.equals(order.getBuyerId()) ? order.getSellerId() : order.getBuyerId();
    }
}
