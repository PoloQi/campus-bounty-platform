package com.campus.bounty.trade;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TradePostRepository extends JpaRepository<TradePost, Long> {
    @Query("select p from TradePost p where p.status = com.campus.bounty.trade.PostStatus.OPEN "
            + "and (p.type <> com.campus.bounty.trade.PostType.BOUNTY or p.deadline is null or p.deadline > :now) "
            + "and (:type is null or p.type = :type) "
            + "and (:category is null or p.category = :category) "
            + "and (:kw is null or p.title like %:kw%) order by p.id desc")
    List<TradePost> search(@Param("type") PostType type, @Param("category") String category,
                           @Param("kw") String keyword, @Param("now") LocalDateTime now);

    default List<TradePost> search(PostType type, String category, String keyword) {
        return search(type, category, keyword, LocalDateTime.now());
    }

    @Query("select p.title from TradePost p where p.type = :type and p.status = :status "
            + "and (p.deadline is null or p.deadline > :now) order by p.id desc")
    List<String> findTitles(@Param("type") PostType type, @Param("status") PostStatus status,
                            @Param("now") LocalDateTime now, Pageable page);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from TradePost p where p.id = :id")
    Optional<TradePost> findByIdForUpdate(@Param("id") Long id);

    Optional<TradePost> findByIdAndStatus(Long id, PostStatus status);

    List<TradePost> findByPublisherIdOrderByIdDesc(Long publisherId);
}
