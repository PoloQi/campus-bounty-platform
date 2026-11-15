package com.campus.bounty.wallet;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {
    Optional<Wallet> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.userId = :userId")
    Optional<Wallet> lockByUserId(@Param("userId") Long userId);

    /**
     * 平台冻结总额。coalesce 兜住空表（SUM 在无行时返回 null），
     * 否则调用方要对 null 做判断，容易漏。
     */
    @Query("select coalesce(sum(w.frozen), 0) from Wallet w")
    BigDecimal totalFrozen();
}
