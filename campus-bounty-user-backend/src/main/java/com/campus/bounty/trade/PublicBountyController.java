package com.campus.bounty.trade;

import com.campus.bounty.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

@RestController
@RequiredArgsConstructor
class PublicBountyController {
    static final String TITLES_PATH = "/api/public/bounty-titles";
    private static final int DISPLAY_LIMIT = 24;
    private static final Pattern CONTACT_OR_PRIVATE_LOCATION = Pattern.compile(
            "(?i)@|(?:https?://|www\\.)|(?:微信|微[信訊]|\\bwechat\\b|\\bvx\\b|\\bqq\\b|电话|手机|邮箱|联系方式|身份证|学号|取件码|收货地址)"
            + "|(?<!\\d)\\d(?:[\\s.()–—-]*\\d){6,}"
            + "|(?:宿舍|寝室|公寓|房间|房号|门牌|门栋|楼|栋|幢|单元)[^\\r\\n，。；]{0,14}\\d{2,}"
            + "|\\d{2,}[\\s]*(?:室|床位|房号|门牌)"
    );
    private static final Pattern INVISIBLE_FORMAT = Pattern.compile("\\p{Cf}");

    private final TradePostRepository tradePosts;

    @GetMapping(TITLES_PATH)
    public ApiResponse<List<String>> titles() {
        List<String> titles = tradePosts.findTitles(PostType.BOUNTY, PostStatus.OPEN, BountyDeadline.now(), PageRequest.of(0, DISPLAY_LIMIT))
                .stream()
                .filter(PublicBountyController::canDisplay)
                .map(String::strip)
                .distinct()
                .toList();
        return ApiResponse.ok(titles);
    }

    private static boolean canDisplay(String title) {
        if (title == null || title.isBlank()) {
            return false;
        }
        String searchable = INVISIBLE_FORMAT.matcher(Normalizer.normalize(title, Normalizer.Form.NFKC)).replaceAll("");
        return !CONTACT_OR_PRIVATE_LOCATION.matcher(searchable).find();
    }
}
