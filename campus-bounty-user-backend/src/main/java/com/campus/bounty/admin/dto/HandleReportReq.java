package com.campus.bounty.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record HandleReportReq(
        @NotNull(message = "请选择举报处理结果") Result result,
        @NotBlank(message = "请填写处理意见") @Size(max = 160, message = "处理意见不能超过160字") String comment,
        Boolean moderate) {
    public enum Result {
        VALID, REJECTED
    }
}
