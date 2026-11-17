package com.campus.bounty.trade;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AppealRepository extends JpaRepository<Appeal, Long> {
    Optional<Appeal> findFirstByOrderIdAndStatusOrderByIdDesc(Long orderId, String status);

    Optional<Appeal> findFirstByOrderIdOrderByIdDesc(Long orderId);

    List<Appeal> findByOrderIdOrderByIdDesc(Long orderId);

    @Query("select a.orderId from Appeal a where a.id = :id")
    Optional<Long> findOrderId(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Appeal a where a.id = :id")
    Optional<Appeal> findByIdForUpdate(@Param("id") Long id);

    @Query(value = """
            select a from Appeal a
            where (:status is null or a.status = :status)
            order by case when a.status = 'OPEN' then 0 else 1 end, a.id desc
            """, countQuery = "select count(a) from Appeal a where (:status is null or a.status = :status)")
    Page<Appeal> listForAdmin(@Param("status") String status, Pageable pageable);
}
