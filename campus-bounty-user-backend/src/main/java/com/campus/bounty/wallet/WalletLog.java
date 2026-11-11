package com.campus.bounty.wallet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "wallet_log")
@Getter
@Setter
public class WalletLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 16)
    private String type;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 10, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "order_id")
    private Long orderId;

    @Column(length = 100)
    private String remark;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public static WalletLog of(Long userId, String type, BigDecimal amount, BigDecimal balanceAfter,
                               Long orderId, String remark) {
        WalletLog log = new WalletLog();
        log.userId = userId;
        log.type = type;
        log.amount = amount;
        log.balanceAfter = balanceAfter;
        log.orderId = orderId;
        log.remark = remark;
        return log;
    }
}
