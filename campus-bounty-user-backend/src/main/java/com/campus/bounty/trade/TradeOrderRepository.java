package com.campus.bounty.trade;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
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

    /**
     * 管理端只读订单检索：六个条件全部可选，按 id 倒序。
     * postType 落在 trade_post 上，故用实体 join 而非关联属性（TradeOrder 未映射 @ManyToOne）。
     * 沿用 TradePostRepository.search 的 (:param is null or ...) 写法，不引入 Specification。
     * 用 left join 而非逗号连接：订单对应的帖子若缺失也不会整行消失，只是 p.type 为 null。
     */
    @Query("select o from TradeOrder o left join TradePost p on p.id = o.postId "
            + "where (:status is null or o.status = :status) "
            + "and (:postType is null or p.type = :postType) "
            + "and (:buyerId is null or o.buyerId = :buyerId) "
            + "and (:sellerId is null or o.sellerId = :sellerId) "
            + "and (:from is null or o.createdAt >= :from) "
            + "and (:to is null or o.createdAt <= :to) order by o.id desc")
    List<TradeOrder> searchForAdmin(@Param("status") OrderStatus status, @Param("postType") PostType postType,
                                    @Param("buyerId") Long buyerId, @Param("sellerId") Long sellerId,
                                    @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * 在途订单应冻金额：这些状态下钱还被托管着，未结算也未退回。
     * 与 WalletRepository.totalFrozen() 的差额是资金监管的核心核对指标。
     */
    @Query("select coalesce(sum(o.amount), 0) from TradeOrder o where o.status in :statuses")
    BigDecimal sumAmountByStatusIn(@Param("statuses") Collection<OrderStatus> statuses);

    @Query("select count(o) from TradeOrder o where o.status in :statuses")
    long countByStatusIn(@Param("statuses") Collection<OrderStatus> statuses);
}
