package com.campus.bounty.community;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {
    List<Post> findByStatusOrderByIdDesc(String status);

    List<Post> findByStatusAndTagsContainingOrderByIdDesc(String status, String tag);

    List<Post> findByAuthorIdOrderByIdDesc(Long authorId);
}
