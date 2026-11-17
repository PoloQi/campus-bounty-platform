package com.campus.bounty.user;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(Long id);

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    @Query("select u.role from User u where u.id = :id and u.status <> 'BANNED'")
    Optional<String> findActiveRole(@Param("id") Long id);
}
