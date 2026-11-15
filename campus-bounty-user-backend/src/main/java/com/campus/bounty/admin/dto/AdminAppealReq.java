package com.campus.bounty.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 仲裁请求体。release / refund 两个接口共用，裁决方向写在 URL 里而不是这个字段里，
 * 所以这里只有处理意见——少一个可传错的枚举字段。
 */
@Data
public class AdminAppealReq {
    /** 设计硬要求：仲裁人必须填写处理意见，会同时进申诉记录、审计与双方通知。 */
    @NotBlank(message = "请填写处理意见")
    @Size(max = 500, message = "处理意见不能超过500字")
    private String comment;
}
