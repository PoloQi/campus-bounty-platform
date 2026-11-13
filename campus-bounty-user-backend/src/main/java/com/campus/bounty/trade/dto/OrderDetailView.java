package com.campus.bounty.trade.dto;

import com.campus.bounty.trade.TradeOrder;
import com.campus.bounty.trade.TradePost;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OrderDetailView {
    private TradeOrder order;
    private TradePost post;
}
