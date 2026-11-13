package com.campus.bounty.notification;

import com.campus.bounty.common.ApiResponse;
import com.campus.bounty.config.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notify")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<List<Notification>> list() {
        return ApiResponse.ok(notificationService.list(CurrentUser.id()));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(CurrentUser.id(), id);
        return ApiResponse.ok();
    }
}
