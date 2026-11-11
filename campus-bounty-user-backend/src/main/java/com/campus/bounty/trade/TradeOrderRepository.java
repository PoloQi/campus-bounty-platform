package com.campus.bounty.trade;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TradeOrderRepository extends JpaRepository<TradeOrder, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from TradeOrder o where o.id = :id")
    Optional<TradeOrder> findByIdForUpdate(@Param("id") Long id);

    List<TradeOrder> findByBuyerIdOrderByIdDesc(Long buyerId);

    List<TradeOrder> findBySellerIdOrderByIdDesc(Long sellerId);

    Optional<TradeOrder> findByPostIdAndStatusNot(Long postId, OrderStatus status);

    Optional<TradeOrder> findByPostId(Long postId);
}
