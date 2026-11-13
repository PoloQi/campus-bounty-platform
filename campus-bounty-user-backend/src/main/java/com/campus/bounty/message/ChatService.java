package com.campus.bounty.message;

import com.campus.bounty.common.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {
    private final MessageRepository messageRepository;

    @Transactional
    public Message save(Long fromId, Long toId, String content) {
        if (content == null || content.isBlank()) {
            throw new BizException("消息不能为空");
        }
        Message message = new Message();
        message.setFromId(fromId);
        message.setToId(toId);
        message.setContent(content);
        return messageRepository.save(message);
    }

    public List<Message> history(Long meId, Long otherId) {
        return messageRepository.conversation(meId, otherId);
    }
}
