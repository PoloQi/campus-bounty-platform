package com.campus.bounty.trade;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "trade_post")
@Getter
@Setter
public class TradePost {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "publisher_id", nullable = false)
    private Long publisherId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PostType type = PostType.BOUNTY;

    @Column(nullable = false, length = 80)
    private String title;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(length = 500)
    private String images;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 30)
    private String category;

    @Column(nullable = false)
    private LocalDateTime deadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PostStatus status = PostStatus.OPEN;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
