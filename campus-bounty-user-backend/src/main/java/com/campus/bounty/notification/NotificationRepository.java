package com.campus.bounty.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserIdOrderByIdDesc(Long userId);

    boolean existsByUserIdAndTypeAndRefIdAndCreatedAtGreaterThanEqual(
            Long userId, String type, Long refId, LocalDateTime createdAt);
}
