package com.campus.bounty.community;

import com.campus.bounty.common.ApiResponse;
import com.campus.bounty.common.FileStorageService;
import com.campus.bounty.config.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/community")
@RequiredArgsConstructor
public class CommunityController {
    private final FileStorageService fileStorageService;

    @PostMapping("/upload")
    public ApiResponse<String> upload(@RequestParam("file") MultipartFile file) {
        CurrentUser.id();
        return ApiResponse.ok(fileStorageService.store(file));
    }
}
