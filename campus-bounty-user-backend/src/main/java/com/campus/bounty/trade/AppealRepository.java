package com.campus.bounty.trade;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AppealRepository extends JpaRepository<Appeal, Long> {
    Optional<Appeal> findFirstByOrderIdAndStatusOrderByIdDesc(Long orderId, String status);

    List<Appeal> findByOrderIdOrderByIdDesc(Long orderId);

    /**
     * 管理端申诉列表：status 可选，**OPEN 永远排在前面**（待办优先），同状态内新的在前。
     * CASE 排序是 JPQL 支持的写法，不必为此引入 Sort/Specification。
     */
    @Query("select a from Appeal a where (:status is null or a.status = :status) "
            + "order by case when a.status = 'OPEN' then 0 else 1 end, a.id desc")
    List<Appeal> searchForAdmin(@Param("status") String status);

    /** 看板待办数：OPEN 申诉条数。 */
    long countByStatus(String status);
}
