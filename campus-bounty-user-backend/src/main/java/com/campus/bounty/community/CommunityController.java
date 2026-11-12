package com.campus.bounty.community;

import com.campus.bounty.common.ApiResponse;
import com.campus.bounty.common.FileStorageService;
import com.campus.bounty.community.dto.CreatePostReq;
import com.campus.bounty.community.dto.PostResp;
import com.campus.bounty.community.dto.UpdatePostReq;
import com.campus.bounty.config.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/community")
@RequiredArgsConstructor
public class CommunityController {
    private final FileStorageService fileStorageService;
    private final PostService postService;

    @PostMapping("/posts")
    public ApiResponse<Post> create(@Valid @RequestBody CreatePostReq request) {
        return ApiResponse.ok(postService.create(CurrentUser.id(), request));
    }

    @GetMapping("/posts")
    public ApiResponse<List<PostResp>> feed(@RequestParam(required = false) String tag) {
        return ApiResponse.ok(postService.feed(tag));
    }

    @GetMapping("/posts/mine")
    public ApiResponse<List<PostResp>> mine() {
        return ApiResponse.ok(postService.mine(CurrentUser.id()));
    }

    @PutMapping("/posts/{id}")
    public ApiResponse<PostResp> update(@PathVariable Long id, @Valid @RequestBody UpdatePostReq request) {
        return ApiResponse.ok(postService.update(id, CurrentUser.id(), request));
    }

    @DeleteMapping("/posts/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        postService.delete(id, CurrentUser.id());
        return ApiResponse.ok();
    }

    @PostMapping("/upload")
    public ApiResponse<String> upload(@RequestParam("file") MultipartFile file) {
        CurrentUser.id();
        return ApiResponse.ok(fileStorageService.store(file));
    }
}
