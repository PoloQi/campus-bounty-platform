package com.campus.bounty.trade;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppealRepository extends JpaRepository<Appeal, Long> {
    Optional<Appeal> findFirstByOrderIdAndStatusOrderByIdDesc(Long orderId, String status);

    List<Appeal> findByOrderIdOrderByIdDesc(Long orderId);
}
