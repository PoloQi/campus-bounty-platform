package com.campus.bounty.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {
    List<Report> findByStatusOrderByIdDesc(String status);

    boolean existsByReporterIdAndTargetTypeAndTargetIdAndStatus(
            Long reporterId, String targetType, Long targetId, String status);

    /**
     * 管理端举报列表：两个条件都可选（null 表示不过滤），按 id 倒序。
     * 沿用仓库既有 {@code (:param is null or ...)} 写法，不引入 Specification。
     */
    @Query("select r from Report r "
            + "where (:status is null or r.status = :status) "
            + "and (:targetType is null or r.targetType = :targetType) "
            + "order by r.id desc")
    List<Report> searchForAdmin(@Param("status") String status, @Param("targetType") String targetType);
}
