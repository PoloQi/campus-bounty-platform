package com.campus.bounty.wallet.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RechargeReq {
    @NotNull(message = "充值金额不能为空")
    @DecimalMin(value = "0.00", inclusive = false, message = "充值金额必须大于 0")
    @Digits(integer = 8, fraction = 2, message = "充值金额最多两位小数")
    private BigDecimal amount;
}
