package com.campus.bounty.trade;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TradePostRepository extends JpaRepository<TradePost, Long> {
    @Query("select p from TradePost p where p.status = com.campus.bounty.trade.PostStatus.OPEN "
            + "and (:type is null or p.type = :type) "
            + "and (:category is null or p.category = :category) "
            + "and (:kw is null or p.title like %:kw%) order by p.id desc")
    List<TradePost> search(@Param("type") PostType type, @Param("category") String category,
                           @Param("kw") String keyword);

    Optional<TradePost> findByIdAndStatus(Long id, PostStatus status);

    List<TradePost> findByPublisherIdOrderByIdDesc(Long publisherId);
}
