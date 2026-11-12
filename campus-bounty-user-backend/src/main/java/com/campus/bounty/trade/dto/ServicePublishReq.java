package com.campus.bounty.trade.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ServicePublishReq {
    @NotBlank(message = "标题不能为空")
    @Size(max = 80, message = "标题最多 80 字")
    private String title;

    @NotBlank(message = "描述不能为空")
    @Size(max = 1000, message = "描述最多 1000 字")
    private String description;

    @Size(max = 500, message = "图片信息最多 500 字")
    private String images;

    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0.00", inclusive = false, message = "价格必须大于 0")
    @Digits(integer = 8, fraction = 2, message = "价格最多两位小数")
    private BigDecimal price;

    @NotBlank(message = "分类不能为空")
    @Size(max = 30, message = "分类最多 30 字")
    private String category;
}
