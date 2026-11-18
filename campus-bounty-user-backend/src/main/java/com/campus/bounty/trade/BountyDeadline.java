package com.campus.bounty.trade;

import java.time.LocalDateTime;
import java.time.ZoneId;

final class BountyDeadline {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private BountyDeadline() {
    }

    static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }
}
