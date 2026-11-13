package com.campus.bounty.message;

import com.campus.bounty.common.ApiResponse;
import com.campus.bounty.config.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chatService;

    @GetMapping("/history")
    public ApiResponse<List<Message>> history(@RequestParam Long withUserId) {
        return ApiResponse.ok(chatService.history(CurrentUser.id(), withUserId));
    }
}
