package com.campus.bounty.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "t_user", uniqueConstraints = @UniqueConstraint(name = "uk_user_username", columnNames = "username"))
@Getter
@Setter
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(length = 30)
    private String nickname;

    @Column(length = 200)
    private String avatar;

    @Column(length = 200)
    private String bio;

    @Column(length = 60)
    private String contact;

    @Column(nullable = false, length = 16)
    private String role = "STUDENT";

    @Column(nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
