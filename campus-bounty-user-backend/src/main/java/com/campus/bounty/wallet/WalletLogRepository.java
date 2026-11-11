package com.campus.bounty.wallet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WalletLogRepository extends JpaRepository<WalletLog, Long> {
    List<WalletLog> findByUserIdOrderByIdDesc(Long userId);
}
