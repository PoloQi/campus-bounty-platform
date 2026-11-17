package com.campus.bounty.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "t_report_resolution")
public class ReportResolution {
    @Id
    private Long reportId;
    @Column(nullable = false, length = 16)
    private String result;
    @Column(nullable = false, length = 500)
    private String comment;
    @Column(nullable = false)
    private Long adminId;
    @Column(nullable = false)
    private LocalDateTime handledAt;
    @Column(length = 24)
    private String moderationAction;

    protected ReportResolution() {
    }

    ReportResolution(Long reportId, String result, String comment, Long adminId, String moderationAction) {
        this.reportId = reportId;
        this.result = result;
        this.comment = comment;
        this.adminId = adminId;
        this.moderationAction = moderationAction;
        this.handledAt = LocalDateTime.now();
    }
}
