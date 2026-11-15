package com.campus.bounty.wallet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WalletLogRepository extends JpaRepository<WalletLog, Long> {
    List<WalletLog> findByUserIdOrderByIdDesc(Long userId);

    /** 管理端订单详情时间线：同一订单的资金流水按发生顺序正序返回（冻结 → 解冻/结算）。 */
    List<WalletLog> findByOrderIdOrderByIdAsc(Long orderId);
}
