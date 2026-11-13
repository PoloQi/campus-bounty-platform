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
    void blank_message_is_not_saved() {
        User first = register("chat_blank_first");
        User second = register("chat_blank_second");

        assertThrows(BizException.class, () -> chatService.save(first.getId(), second.getId(), "  "));
        assertEquals(0, chatService.history(first.getId(), second.getId()).size());
    }

    private User register(String username) {
        return userService.register(username, "123456", "123456", "私信用户");
    }
}
