package com.campus.bounty.user;

import com.campus.bounty.common.ApiResponse;
import com.campus.bounty.config.CurrentUser;
import com.campus.bounty.user.dto.UpdateProfileReq;
import com.campus.bounty.user.dto.UserProfileResp;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/me")
    public ApiResponse<UserProfileResp> me() {
        return ApiResponse.ok(userService.profile(CurrentUser.id()));
    }

    @PutMapping("/me")
    public ApiResponse<UserProfileResp> update(@Valid @RequestBody UpdateProfileReq request) {
        Long userId = CurrentUser.id();
        userService.updateProfile(userId, request);
        return ApiResponse.ok(userService.profile(userId));
    }
}
