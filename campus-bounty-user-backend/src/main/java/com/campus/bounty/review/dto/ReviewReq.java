package com.campus.bounty.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReviewReq {
    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    @NotBlank(message = "评价内容不能为空")
    @Size(max = 500, message = "评价内容不能超过500字")
    private String content;
}
