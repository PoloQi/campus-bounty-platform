package com.campus.bounty.admin;

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
@Table(name = "t_report")
@Getter
@Setter
public class Report {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_type", nullable = false, length = 16)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @Column(nullable = false, length = 200)
    private String reason;

    @Column(nullable = false, length = 16)
    private String status = "PENDING";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** 处置人。列在 B1 已随 schema.sql 落入数据库，本板块只补实体字段。 */
    @Column(name = "handler_id")
    private Long handlerId;

    @Column(name = "handled_at")
    private LocalDateTime handledAt;

    /** VALID 举报成立 / REJECTED 举报不成立；未处置时为 null。 */
    @Column(length = 16)
    private String result;

    @Column(name = "handle_comment", length = 500)
    private String handleComment;
}
