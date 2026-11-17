package com.campus.bounty.trade;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TradeOrderRepository extends JpaRepository<TradeOrder, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from TradeOrder o where o.id = :id")
    Optional<TradeOrder> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select o.id from TradeOrder o, TradePost p
            where o.postId = p.id
              and o.status = com.campus.bounty.trade.OrderStatus.PENDING and o.sellerId is null
              and p.type = com.campus.bounty.trade.PostType.BOUNTY
              and p.status = com.campus.bounty.trade.PostStatus.OPEN and p.deadline <= :now
              and not exists (
                  select n.id from Notification n where n.type = :notificationType
                    and n.refId = o.id and n.userId = o.buyerId and n.createdAt >= p.deadline
              )
            order by o.id
            """)
    List<Long> findDueBountyNoticeIds(@Param("now") LocalDateTime now,
                                     @Param("notificationType") String notificationType, Pageable page);

    List<TradeOrder> findByBuyerIdOrderByIdDesc(Long buyerId);

    List<TradeOrder> findBySellerIdOrderByIdDesc(Long sellerId);

    Optional<TradeOrder> findByPostIdAndStatusNot(Long postId, OrderStatus status);

    Optional<TradeOrder> findByPostId(Long postId);
}
