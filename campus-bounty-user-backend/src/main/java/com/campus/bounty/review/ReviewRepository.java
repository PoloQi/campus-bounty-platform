package com.campus.bounty.review;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByToUserIdOrderByIdDesc(Long toUserId);
    List<Review> findByOrderIdOrderByIdAsc(Long orderId);

    boolean existsByOrderIdAndFromUserId(Long orderId, Long fromUserId);
}
