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

    // ---------- B7 数据看板 ----------

    long countByStatus(OrderStatus status);

    /**
     * 看板：按状态分组计数，返回 [status, count]。
     * 一次查询拿到全部分组，比 6 次 countByStatus 少 5 次往返；
     * 库里没有的状态不会出现在结果里，补零是 Service 的责任（保证 6 态齐全）。
     */
    @Query("select o.status, count(o) from TradeOrder o group by o.status")
    List<Object[]> countGroupByStatus();

    /**
     * 看板：按自然日统计新建订单数，返回 [year, month, day, count]。
     * <p>
     * 用 {@code extract(...)} 而不是 {@code function('date', ...)} 或 {@code date(...)}：
     * extract 是 JPQL 标准语法，H2 与 MySQL 都能翻译，不必为测试库和生产库写两套。
     * 只返回「有数据的天」，缺的日期由 Service 补 0。
     */
    @Query("select extract(year from o.createdAt), extract(month from o.createdAt), "
            + "extract(day from o.createdAt), count(o) from TradeOrder o "
            + "where o.createdAt >= :from "
            + "group by extract(year from o.createdAt), extract(month from o.createdAt), "
            + "extract(day from o.createdAt)")
    List<Object[]> countCreatedByDay(@Param("from") LocalDateTime from);

    /**
     * 看板：按自然日统计完成订单数，返回 [year, month, day, count]。
     * <p>
     * <b>重要口径</b>：trade_order 没有 completed_at 列（不在本批次改库范围内），
     * 完成里程碑只能借用 updated_at。这是已知的近似：已完成订单若日后被再次改动，
     * 会被算进改动那天。当前代码路径下 COMPLETED 是终态、不会再被写，
     * 所以现阶段两者等价——将来若给已完成订单加了可编辑属性，这里必须同步改口径。
     */
    @Query("select extract(year from o.updatedAt), extract(month from o.updatedAt), "
            + "extract(day from o.updatedAt), count(o) from TradeOrder o "
            + "where o.status = :status and o.updatedAt >= :from "
            + "group by extract(year from o.updatedAt), extract(month from o.updatedAt), "
            + "extract(day from o.updatedAt)")
    List<Object[]> countUpdatedByDay(@Param("status") OrderStatus status,
                                     @Param("from") LocalDateTime from);
}
