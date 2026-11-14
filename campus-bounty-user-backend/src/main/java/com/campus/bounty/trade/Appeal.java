package com.campus.bounty.trade;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "t_appeal")
@Getter
@Setter
public class Appeal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "appellant_id", nullable = false)
    private Long appellantId;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "from_status", nullable = false, length = 16)
    private String fromStatus;

    @Column(nullable = false, length = 16)
    private String status = "OPEN";

    @Column(length = 24)
    private String outcome;

    @Column(name = "admin_id")
    private Long adminId;

    @Column(name = "admin_comment", length = 500)
    private String adminComment;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "handled_at")
    private LocalDateTime handledAt;
}
