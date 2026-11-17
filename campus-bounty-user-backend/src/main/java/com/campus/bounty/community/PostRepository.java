package com.campus.bounty.community;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Post p where p.id = :id")
    Optional<Post> findByIdForUpdate(Long id);

    List<Post> findByStatusOrderByIdDesc(String status);

    List<Post> findByStatusAndTagsContainingOrderByIdDesc(String status, String tag);

    List<Post> findByAuthorIdOrderByIdDesc(Long authorId);
}
