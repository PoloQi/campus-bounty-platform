package com.campus.bounty.notification;

import com.campus.bounty.common.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notificationRepository;

    public void push(Long userId, String type, String content, Long refId) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setContent(content);
        notification.setRefId(refId);
        notificationRepository.save(notification);
    }

    public List<Notification> list(Long userId) {
        return notificationRepository.findByUserIdOrderByIdDesc(userId);
    }

    @Transactional
    public void markRead(Long userId, Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new BizException("通知不存在"));
        if (!notification.getUserId().equals(userId)) {
            throw new BizException("无权操作");
        }
        notification.setRead(true);
        notificationRepository.save(notification);
    }
}
