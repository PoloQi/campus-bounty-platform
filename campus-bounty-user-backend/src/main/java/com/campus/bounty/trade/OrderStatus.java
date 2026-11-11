package com.campus.bounty.trade;

// DISPUTED 为 Sprint 3 预留，当前批次不产生该状态。
public enum OrderStatus {
    PENDING,
    IN_PROGRESS,
    CONFIRMING,
    DISPUTED,
    COMPLETED,
    CANCELLED
}
