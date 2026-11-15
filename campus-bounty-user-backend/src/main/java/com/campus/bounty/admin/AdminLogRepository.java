package com.campus.bounty.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AdminLogRepository extends JpaRepository<AdminLog, Long> {
    /**
     * 四条件全部可选。沿用 TradePostRepository.search 的 (:param is null or ...) 写法，
     * 不引入 Specification —— 全库零使用，保持一致范式。
     */
    @Query("select l from AdminLog l where (:adminId is null or l.adminId = :adminId) "
            + "and (:action is null or l.action = :action) "
            + "and (:from is null or l.createdAt >= :from) "
            + "and (:to is null or l.createdAt <= :to) order by l.id desc")
    List<AdminLog> search(@Param("adminId") Long adminId, @Param("action") String action,
                          @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
