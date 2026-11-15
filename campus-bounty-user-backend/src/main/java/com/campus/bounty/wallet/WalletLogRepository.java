package com.campus.bounty.wallet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface WalletLogRepository extends JpaRepository<WalletLog, Long> {
    List<WalletLog> findByUserIdOrderByIdDesc(Long userId);

    /** 管理端订单详情时间线：同一订单的资金流水按发生顺序正序返回（冻结 → 解冻/结算）。 */
    List<WalletLog> findByOrderIdOrderByIdAsc(Long orderId);

    /**
     * 管理端全平台流水检索：四个条件全部可选，按 id 倒序。
     * userId 为空即查全平台；传了不存在的 userId 返回空列表（不报错，它是筛选条件而非资源标识）。
     */
    @Query("select l from WalletLog l where (:userId is null or l.userId = :userId) "
            + "and (:type is null or l.type = :type) "
            + "and (:from is null or l.createdAt >= :from) "
            + "and (:to is null or l.createdAt <= :to) order by l.id desc")
    List<WalletLog> searchForAdmin(@Param("userId") Long userId, @Param("type") String type,
                                   @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
