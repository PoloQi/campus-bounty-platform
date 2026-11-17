package com.campus.bounty.trade.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record ExtendDeadlineReq(@NotNull(message = "截止时间不能为空") LocalDateTime deadline) {
}
