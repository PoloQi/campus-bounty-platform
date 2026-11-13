package com.campus.bounty.review;

import com.campus.bounty.common.ApiResponse;
import com.campus.bounty.config.CurrentUser;
import com.campus.bounty.review.dto.ReviewReq;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {
    private final ReviewService reviewService;

    @PostMapping
    public ApiResponse<Long> review(@Valid @RequestBody ReviewReq request) {
        return ApiResponse.ok(reviewService.review(
                request.getOrderId(), CurrentUser.id(), request.getContent()).getId());
    }

    @GetMapping("/user/{userId}")
    public ApiResponse<List<Review>> ofUser(@PathVariable Long userId) {
        return ApiResponse.ok(reviewService.ofUser(userId));
    }
}
