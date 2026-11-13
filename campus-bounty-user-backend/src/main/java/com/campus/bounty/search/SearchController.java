package com.campus.bounty.search;

import com.campus.bounty.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {
    private final SearchService searchService;

    @GetMapping
    public ApiResponse<Map<String, Object>> search(@RequestParam String keyword) {
        return ApiResponse.ok(searchService.search(keyword));
    }
}
