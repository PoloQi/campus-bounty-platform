package com.campus.bounty.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReportReq {
    @NotBlank(message = "举报类型不能为空")
    @Pattern(regexp = "POST|TRADE_POST|USER", message = "举报类型不合法")
    private String targetType;

    @NotNull(message = "举报对象ID不能为空")
    private Long targetId;

    @NotBlank(message = "举报原因不能为空")
    @Size(max = 200, message = "举报原因不能超过200字")
    private String reason;
}
