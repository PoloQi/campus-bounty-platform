package com.campus.bounty.message;

import com.campus.bounty.common.BizException;
import com.campus.bounty.user.User;
import com.campus.bounty.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class ChatServiceTest {
    @Autowired
    ChatService chatService;
    @Autowired
    UserService userService;

    @Test
    void history_contains_only_two_users_messages_in_send_order() {
        User first = register("chat_history_first");
        User second = register("chat_history_second");
        User outsider = register("chat_history_outsider");

        chatService.save(first.getId(), second.getId(), "在吗");
        chatService.save(second.getId(), first.getId(), "在的");
        chatService.save(first.getId(), outsider.getId(), "其他人的消息");

        List<Message> history = chatService.history(first.getId(), second.getId());

        assertEquals(2, history.size());
        assertEquals("在吗", history.get(0).getContent());
        assertEquals("在的", history.get(1).getContent());
        assertEquals(second.getId(), history.get(0).getToId());
        assertEquals(first.getId(), history.get(1).getToId());
    }

    @Test
    void recent_conversations_include_offline_incoming_messages_and_use_latest_id_per_peer() {
        User owner = register("chat_list_owner");
        User first = register("chat_list_first");
        User second = register("chat_list_second");
        User unrelated = register("chat_list_unrelated");

        chatService.save(owner.getId(), first.getId(), "第一条");
        Message incoming = chatService.save(second.getId(), owner.getId(), "离线来信");
        Message latest = chatService.save(first.getId(), owner.getId(), "最新回复");
        chatService.save(first.getId(), unrelated.getId(), "其他会话");

        var conversations = chatService.recentConversations(owner.getId());

        assertEquals(List.of(first.getId(), second.getId()),
                conversations.stream().map(MessageRepository.ConversationSummary::getPeerId).toList());
        assertEquals(List.of(latest.getId(), incoming.getId()),
                conversations.stream().map(MessageRepository.ConversationSummary::getLastMessageId).toList());
    }

    @Test
    void blank_message_is_not_saved() {
        User first = register("chat_blank_first");
        User second = register("chat_blank_second");

        assertThrows(BizException.class, () -> chatService.save(first.getId(), second.getId(), "  "));
        assertEquals(0, chatService.history(first.getId(), second.getId()).size());
    }

    @Test
    void message_to_missing_user_is_not_saved() {
        User sender = register("chat_missing_sender");

        assertThrows(BizException.class, () -> chatService.save(sender.getId(), 99999999L, "你好"));
        assertEquals(0, chatService.history(sender.getId(), 99999999L).size());
    }

    @Test
    void message_over_database_limit_is_rejected() {
        User sender = register("chat_long_sender");
        User receiver = register("chat_long_receiver");

        assertThrows(BizException.class,
                () -> chatService.save(sender.getId(), receiver.getId(), "x".repeat(1001)));
        assertEquals(0, chatService.history(sender.getId(), receiver.getId()).size());
    }

    private User register(String username) {
        return userService.register(username, "123456", "123456", "私信用户");
    }
}
