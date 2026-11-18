package com.campus.bounty.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Table(name = "t_admin_log")
@Getter
public class AdminLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long adminId;
    @Column(nullable = false, length = 24)
    private String action;
    @Column(length = 24)
    private String targetType;
    private Long targetId;
    @Column(length = 500)
    private String detail;
    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected AdminLog() {
    }

    AdminLog(Long adminId, String action, String targetType, Long targetId, String detail) {
        this.adminId = adminId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.detail = detail;
        this.createdAt = LocalDateTime.now();
    }
}
