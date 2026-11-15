package com.campus.bounty.admin.dto;

import com.campus.bounty.trade.Appeal;
import com.campus.bounty.trade.TradeOrder;
import com.campus.bounty.trade.TradePost;
import com.campus.bounty.wallet.WalletLog;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 管理端订单详情：一条订单的完整时间线聚合。
 * <p>
 * 五个部分对应对账/仲裁时要回答的五个问题：钱在这笔订单上怎么走的（order + walletLogs）、
 * 交易的是什么（post）、双方是谁（buyer/seller）、有没有纠纷（appeals）。
 * 拆成独立 DTO 而不是塞 Map，是为了让前端字段名和类型有契约。
 */
@Data
@AllArgsConstructor
public class AdminOrderDetailView {
    private TradeOrder order;
    /** 订单对应的交易帖；理论上必存在，缺失时返回 null 而不是 500，便于排查脏数据。 */
    private TradePost post;
    private UserBrief buyer;
    /** 悬赏未接单时为 null（PENDING 状态的 seller_id 尚为空）。 */
    private UserBrief seller;
    /** 该订单关联的资金流水，按 id 正序（冻结在前、结算/解冻在后）。 */
    private List<WalletLog> walletLogs;
    /** 该订单的历史申诉，按 id 倒序（最新的在前）。 */
    private List<Appeal> appeals;
}
