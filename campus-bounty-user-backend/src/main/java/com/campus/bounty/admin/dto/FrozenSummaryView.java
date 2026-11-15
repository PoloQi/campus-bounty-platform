package com.campus.bounty.admin.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 平台冻结资金核对结果（设计 §7 的「答辩亮点」指标）。
 * <p>
 * 恒等式：{@code SUM(wallet.frozen)} 应恒等于在途订单金额之和。
 * 因为所有资金路径都守恒——发布冻结、取消解冻、正常结算、仲裁退款/放款——
 * 任何不为 0 的差额都说明有资金脱离了订单，是必须排查的信号。
 * <p>
 * 注意：本类只呈现事实，不在服务层做「差额非 0 就报错」的处理。测试环境里有
 * 直接用合成订单号冻结的用例（见 WalletDomainTest），差额本就不为 0，
 * 报错会让接口在这里挂掉；该由人看数字。
 */
@Data
public class FrozenSummaryView {
    /** 平台冻结总额 = SUM(wallet.frozen) */
    private BigDecimal frozenTotal;
    /** 在途订单应冻金额 = SUM(trade_order.amount) WHERE status IN (PENDING, IN_PROGRESS, CONFIRMING, DISPUTED) */
    private BigDecimal inFlightAmount;
    /** 差额 = frozenTotal - inFlightAmount，正常为 0 */
    private BigDecimal diff;
    /** diff 是否为 0，前端不必自己比较 */
    private boolean balanced;
    /** 在途订单笔数，金额对不上时用来判断是「一笔大额」还是「多笔小额」 */
    private long inFlightOrderCount;

    public static FrozenSummaryView of(BigDecimal frozenTotal, BigDecimal inFlightAmount,
                                       long inFlightOrderCount) {
        BigDecimal diff = frozenTotal.subtract(inFlightAmount);
        FrozenSummaryView view = new FrozenSummaryView();
        view.frozenTotal = frozenTotal;
        view.inFlightAmount = inFlightAmount;
        view.diff = diff;
        view.balanced = diff.compareTo(BigDecimal.ZERO) == 0;
        view.inFlightOrderCount = inFlightOrderCount;
        return view;
    }
}
