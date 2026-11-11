package com.campus.bounty.user;

import com.campus.bounty.common.ApiResponse;
import com.campus.bounty.config.JwtUtil;
import com.campus.bounty.user.dto.LoginReq;
import com.campus.bounty.user.dto.LoginResp;
import com.campus.bounty.user.dto.RegisterReq;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserService userService;
    private final JwtUtil jwtUtil;

    @PostMapping("/register")
    public ApiResponse<Long> register(@Valid @RequestBody RegisterReq request) {
        User user = userService.register(request.getUsername(), request.getPassword(),
                request.getConfirmPassword(), request.getNickname());
        return ApiResponse.ok(user.getId());
    }

    @PostMapping("/login")
    public ApiResponse<LoginResp> login(@Valid @RequestBody LoginReq request) {
        User user = userService.login(request.getUsername(), request.getPassword());
        String token = jwtUtil.generate(user.getId(), user.getUsername());
        LoginResp response = new LoginResp(token, user.getId(), user.getNickname(), user.getRole());
        return ApiResponse.ok(response);
    }
}
