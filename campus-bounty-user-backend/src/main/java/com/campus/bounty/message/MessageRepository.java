package com.campus.bounty.message;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    @Query("select m from Message m where (m.fromId = :first and m.toId = :second) " +
            "or (m.fromId = :second and m.toId = :first) order by m.id asc")
    List<Message> conversation(@Param("first") Long first, @Param("second") Long second);

    @Query(value = """
            select peers.peer_id, max(peers.message_id) as last_message_id
            from (
                select to_id as peer_id, max(id) as message_id
                from t_message where from_id = :userId and to_id <> :userId group by to_id
                union all
                select from_id as peer_id, max(id) as message_id
                from t_message where to_id = :userId and from_id <> :userId group by from_id
            ) peers
            group by peers.peer_id order by max(peers.message_id) desc
            """, nativeQuery = true)
    List<ConversationSummary> recentConversations(@Param("userId") Long userId);

    interface ConversationSummary {
        Long getPeerId();
        Long getLastMessageId();
    }
}
