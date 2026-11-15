package com.campus.bounty.admin.dto;

import com.campus.bounty.trade.Appeal;
import com.campus.bounty.trade.TradeOrder;
import com.campus.bounty.trade.TradePost;
import com.campus.bounty.wallet.WalletLog;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 仲裁台的单条视图：裁决前必须能看清「谁申诉、钱在哪、交易的是什么」。
 * <p>
 * 与 {@link AdminOrderDetailView} 的区别：那个以订单为中心（含历史申诉列表），
 * 这个以申诉为中心，并把 {@code appellantRole} 直接算好——仲裁人最需要的判断依据是
 * 「申诉方是买方还是卖方」，让他自己在两个 id 之间比对容易看错。
 */
@Data
@AllArgsConstructor
public class AppealDetailView {
    private Appeal appeal;
    private TradeOrder order;
    private TradePost post;
    private UserBrief appellant;
    private UserBrief buyer;
    private UserBrief seller;
    /** BUYER / SELLER / UNKNOWN（脏数据：申诉人既不是买方也不是卖方）。 */
    private String appellantRole;
    /** 该订单的资金流水，按 id 正序，仲裁前可据此核对冻结金额是否还在。 */
    private List<WalletLog> walletLogs;
}
