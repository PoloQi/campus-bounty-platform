package com.campus.bounty.message;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    @Query("select m from Message m where (m.fromId = :first and m.toId = :second) " +
            "or (m.fromId = :second and m.toId = :first) order by m.id asc")
    List<Message> conversation(@Param("first") Long first, @Param("second") Long second);
}
