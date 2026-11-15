package com.campus.bounty.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    /**
     * 管理端用户检索：五个条件全部可选，按 id 倒序。
     * keyword 同时模糊匹配用户名与昵称；nickname 为 null 时该分支结果为 null，
     * 由 username 分支兜底（SQL 三值逻辑：false or null = null，即不命中，符合预期）。
     */
    @Query("select u from User u where (:keyword is null or u.username like %:keyword% "
            + "or u.nickname like %:keyword%) "
            + "and (:role is null or u.role = :role) "
            + "and (:status is null or u.status = :status) "
            + "and (:from is null or u.createdAt >= :from) "
            + "and (:to is null or u.createdAt <= :to) order by u.id desc")
    List<User> searchForAdmin(@Param("keyword") String keyword, @Param("role") String role,
                              @Param("status") String status,
                              @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
