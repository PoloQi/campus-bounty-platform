package com.campus.bounty.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class HandleReportReq {
    @NotBlank(message = "处置动作不能为空")
    @Pattern(regexp = "DELETE_POST|CLOSE_TRADE_POST|BAN_USER|REJECT", message = "处置动作不合法")
    private String action;

    /** 处置说明，选填；举报人可在通知里看到结论，管理员备注只入报表与审计。 */
    @Size(max = 500, message = "处置说明不能超过500字")
    private String comment;
}
