package com.campus.bounty.admin.dto;

import com.campus.bounty.trade.Appeal;
import com.campus.bounty.trade.TradeOrder;
import com.campus.bounty.trade.TradePost;
import com.campus.bounty.user.User;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 管理端申诉列表的一行。
 * <p>
 * 列表页要让管理员**不点进去就能判断轻重**：谁的申诉、哪一单、多少钱、什么理由。
 * 所以除了 Appeal 本体，还带上了订单金额/状态与帖子标题。
 */
@Data
@AllArgsConstructor
public class AppealListItem {
    private Long id;
    private Long orderId;
    private Long appellantId;
    /** 显示用名字，昵称优先，没有昵称回落到用户名。 */
    private String appellantName;
    private String reason;
    /** 发起申诉时订单处于哪个状态，撤回时会回到这里。 */
    private String fromStatus;
    private String status;
    private String outcome;
    private LocalDateTime createdAt;
    private LocalDateTime handledAt;

    /** 订单侧摘要，订单缺失（脏数据）时为 null，不抛 500。 */
    private BigDecimal orderAmount;
    private String orderStatus;
    private Long postId;
    private String postTitle;
    /** BOUNTY / SERVICE，决定裁决后要不要关帖。 */
    private String postType;

    public static AppealListItem of(Appeal appeal, TradeOrder order, TradePost post, User appellant) {
        return new AppealListItem(
                appeal.getId(),
                appeal.getOrderId(),
                appeal.getAppellantId(),
                appellant == null ? null : displayName(appellant),
                appeal.getReason(),
                appeal.getFromStatus(),
                appeal.getStatus(),
                appeal.getOutcome(),
                appeal.getCreatedAt(),
                appeal.getHandledAt(),
                order == null ? null : order.getAmount(),
                order == null ? null : order.getStatus().name(),
                order == null ? null : order.getPostId(),
                post == null ? null : post.getTitle(),
                post == null ? null : post.getType().name());
    }

    private static String displayName(User user) {
        return user.getNickname() == null || user.getNickname().isBlank()
                ? user.getUsername()
                : user.getNickname();
    }
}
