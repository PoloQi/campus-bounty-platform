package com.campus.bounty.admin;

import com.campus.bounty.admin.dto.HandleReportReq;
import com.campus.bounty.common.BizException;
import com.campus.bounty.community.Post;
import com.campus.bounty.community.PostRepository;
import com.campus.bounty.notification.NotificationService;
import com.campus.bounty.trade.PostStatus;
import com.campus.bounty.trade.TradePost;
import com.campus.bounty.trade.TradePostRepository;
import com.campus.bounty.user.User;
import com.campus.bounty.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 举报处置闭环（设计 §5）。
 * <p>
 * 处置动作在这里直接改目标对象的状态，**不复用** {@code AdminService.deletePost/closeTradePost/banUser}：
 * 那三个方法各自会写一条 DELETE_POST / CLOSE_TRADE_POST / BAN_USER 审计，
 * 复用就会变成一次举报产生两条审计，台账与证据链都会失真。
 * 本类只写一条 {@link AuditAction#HANDLE_REPORT}。
 */
@Service
@RequiredArgsConstructor
public class AdminReportService {
    public static final String ACTION_DELETE_POST = "DELETE_POST";
    public static final String ACTION_CLOSE_TRADE_POST = "CLOSE_TRADE_POST";
    public static final String ACTION_BAN_USER = "BAN_USER";
    public static final String ACTION_REJECT = "REJECT";

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_HANDLED = "HANDLED";
    private static final String RESULT_VALID = "VALID";
    private static final String RESULT_REJECTED = "REJECTED";
    private static final String NOTIFY_TYPE = "REPORT_HANDLED";

    /** 举报对象类型 → 允许的处置动作。对 POST 举报只能删帖，对 USER 举报只能封禁，避免「拿 A 的举报处置 B」。 */
    private static final Map<String, Set<String>> ALLOWED_ACTIONS = Map.of(
            "POST", Set.of(ACTION_DELETE_POST, ACTION_BAN_USER, ACTION_REJECT),
            "TRADE_POST", Set.of(ACTION_CLOSE_TRADE_POST, ACTION_BAN_USER, ACTION_REJECT),
            "USER", Set.of(ACTION_BAN_USER, ACTION_REJECT));

    private final AdminService adminService;
    private final ReportRepository reportRepository;
    private final PostRepository postRepository;
    private final TradePostRepository tradePostRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    public List<Report> list(Long adminId, String status, String targetType) {
        adminService.requireAdmin(adminId);
        return reportRepository.searchForAdmin(blankToNull(status), blankToNull(targetType));
    }

    @Transactional
    public Report handle(Long adminId, Long reportId, HandleReportReq request) {
        adminService.requireAdmin(adminId);
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BizException("举报不存在"));
        if (!STATUS_PENDING.equals(report.getStatus())) {
            throw new BizException("该举报已处理，不能重复处置");
        }

        String action = request.getAction();
        Set<String> allowed = ALLOWED_ACTIONS.getOrDefault(report.getTargetType(), Set.of());
        if (!allowed.contains(action)) {
            throw new BizException("举报对象类型 " + report.getTargetType()
                    + " 不支持处置动作 " + action);
        }

        String disposal = dispose(action, report);
        boolean valid = !ACTION_REJECT.equals(action);

        report.setStatus(STATUS_HANDLED);
        report.setResult(valid ? RESULT_VALID : RESULT_REJECTED);
        report.setHandlerId(adminId);
        report.setHandledAt(LocalDateTime.now());
        report.setHandleComment(request.getComment());
        Report saved = reportRepository.save(report);

        auditLogService.record(adminId, AuditAction.HANDLE_REPORT, "REPORT", reportId,
                "处理举报#" + reportId + "（" + report.getTargetType() + ":" + report.getTargetId()
                        + "）action=" + action + " result=" + saved.getResult() + "；" + disposal);
        notificationService.push(report.getReporterId(), NOTIFY_TYPE,
                notificationText(valid, action, request.getComment()), reportId);
        return saved;
    }

    private String dispose(String action, Report report) {
        return switch (action) {
            case ACTION_DELETE_POST -> deleteCommunityPost(report.getTargetId());
            case ACTION_CLOSE_TRADE_POST -> closeTradePost(report.getTargetId());
            case ACTION_BAN_USER -> banTargetUser(report);
            default -> "仅驳回，未对目标做任何处置";
        };
    }

    private String deleteCommunityPost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BizException("被举报的社区帖不存在"));
        if ("DELETED".equals(post.getStatus())) {
            return "社区帖#" + postId + " 已是删除状态";
        }
        post.setStatus("DELETED");
        postRepository.save(post);
        return "删除社区帖#" + postId + "（作者ID=" + post.getAuthorId() + "）";
    }

    private String closeTradePost(Long postId) {
        TradePost post = tradePostRepository.findById(postId)
                .orElseThrow(() -> new BizException("被举报的交易帖不存在"));
        if (PostStatus.CLOSED == post.getStatus()) {
            return "交易帖#" + postId + " 已处于下架状态";
        }
        post.setStatus(PostStatus.CLOSED);
        tradePostRepository.save(post);
        return "下架交易帖#" + postId + "（" + post.getType() + " / " + post.getTitle() + "）";
    }

    private String banTargetUser(Report report) {
        Long userId = resolveTargetUserId(report);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException("被举报用户不存在"));
        if ("BANNED".equals(user.getStatus())) {
            return "用户 " + user.getUsername() + " 已是封禁状态";
        }
        user.setStatus("BANNED");
        userRepository.save(user);
        return "封禁用户 " + user.getUsername() + "（ID=" + userId + "）";
    }

    /** BAN_USER 作用于「被举报内容的作者」；target_type=USER 时直接作用于 target_id。 */
    private Long resolveTargetUserId(Report report) {
        return switch (report.getTargetType()) {
            case "USER" -> report.getTargetId();
            case "POST" -> postRepository.findById(report.getTargetId())
                    .orElseThrow(() -> new BizException("被举报的社区帖不存在"))
                    .getAuthorId();
            case "TRADE_POST" -> tradePostRepository.findById(report.getTargetId())
                    .orElseThrow(() -> new BizException("被举报的交易帖不存在"))
                    .getPublisherId();
            default -> throw new BizException("未知的举报对象类型：" + report.getTargetType());
        };
    }

    /** 通知正文限 200 字（t_notification.content），所以只放结论，不塞管理员备注。 */
    private String notificationText(boolean valid, String action, String comment) {
        if (!valid) {
            return "你的举报经核实不成立，已驳回";
        }
        String what = switch (action) {
            case ACTION_DELETE_POST -> "违规内容已删除";
            case ACTION_CLOSE_TRADE_POST -> "违规交易帖已下架";
            case ACTION_BAN_USER -> "违规用户已被封禁";
            default -> "已处置";
        };
        String suffix = (comment == null || comment.isBlank()) ? "" : "（" + truncate(comment, 120) + "）";
        return "你的举报已成立，" + what + suffix;
    }

    private String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 3) + "...";
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
