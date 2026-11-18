package com.campus.bounty.admin;

import com.campus.bounty.admin.dto.HandleReportReq;
import com.campus.bounty.common.BizException;
import com.campus.bounty.community.Post;
import com.campus.bounty.community.PostRepository;
import com.campus.bounty.message.UserBanned;
import com.campus.bounty.notification.NotificationService;
import com.campus.bounty.trade.PostStatus;
import com.campus.bounty.trade.TradePost;
import com.campus.bounty.trade.TradePostRepository;
import com.campus.bounty.user.User;
import com.campus.bounty.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final TradePostRepository tradePostRepository;
    private final ReportRepository reportRepository;
    private final AdminAuditService audit;
    private final NotificationService notifications;
    private final ApplicationEventPublisher events;

    public void requireAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException("用户不存在"));
        if (!"ADMIN".equals(user.getRole()) || !"ACTIVE".equals(user.getStatus())) {
            throw new BizException(40300, "需要管理员权限");
        }
    }

    @Transactional
    public void deletePost(Long adminId, Long postId) {
        requireAdmin(adminId);
        removePost(adminId, postId);
    }

    private void removePost(Long adminId, Long postId) {
        Post post = postRepository.findByIdForUpdate(postId)
                .orElseThrow(() -> new BizException("帖子不存在"));
        post.setStatus("DELETED");
        audit.record(adminId, "DELETE_POST", "POST", postId, "删除社区帖子");
    }

    @Transactional
    public void closeTradePost(Long adminId, Long postId) {
        requireAdmin(adminId);
        closeTrade(adminId, postId);
    }

    private void closeTrade(Long adminId, Long postId) {
        TradePost post = tradePostRepository.findByIdForUpdate(postId)
                .orElseThrow(() -> new BizException("交易帖不存在"));
        post.setStatus(PostStatus.CLOSED);
        audit.record(adminId, "CLOSE_TRADE_POST", "TRADE_POST", postId, post.getTitle());
    }

    @Transactional
    public void banUser(Long adminId, Long userId) {
        requireAdmin(adminId);
        changeStudentStatus(adminId, userId, true);
    }

    @Transactional
    public void unbanUser(Long adminId, Long userId) {
        requireAdmin(adminId);
        changeStudentStatus(adminId, userId, false);
    }

    private void changeStudentStatus(Long adminId, Long userId, boolean ban) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BizException("用户不存在"));
        if (!"STUDENT".equals(user.getRole())) {
            throw new BizException("只可治理学生账号");
        }
        boolean newlyBanned = ban && !"BANNED".equals(user.getStatus());
        user.setStatus(ban ? "BANNED" : "ACTIVE");
        audit.record(adminId, ban ? "BAN_USER" : "UNBAN_USER", "USER", userId, user.getUsername());
        if (newlyBanned) events.publishEvent(new UserBanned(userId));
    }

    @Transactional
    public void report(Long reporterId, String targetType, Long targetId, String reason) {
        userRepository.findByIdForUpdate(reporterId).orElseThrow(() -> new BizException("用户不存在"));
        boolean targetExists = switch (targetType) {
            case "POST" -> postRepository.existsById(targetId);
            case "TRADE_POST" -> tradePostRepository.existsById(targetId);
            case "USER" -> userRepository.existsById(targetId);
            default -> throw new BizException("举报对象类型不合法");
        };
        if (!targetExists) throw new BizException("举报对象不存在");
        if (reportRepository.existsByReporterIdAndTargetTypeAndTargetIdAndStatus(
                reporterId, targetType, targetId, "PENDING")) {
            throw new BizException("你已提交过对该对象的举报，正在处理中");
        }
        Report report = new Report();
        report.setReporterId(reporterId);
        report.setTargetType(targetType);
        report.setTargetId(targetId);
        report.setReason(reason);
        reportRepository.save(report);
    }

    public List<Report> pendingReports(Long adminId) {
        requireAdmin(adminId);
        return reportRepository.findByStatusOrderByIdDesc("PENDING");
    }

    @Transactional
    public void handleReport(Long adminId, Long reportId, HandleReportReq request) {
        requireAdmin(adminId);
        Report report = reportRepository.findByIdForUpdate(reportId)
                .orElseThrow(() -> new BizException("举报不存在"));
        if (!"PENDING".equals(report.getStatus())) {
            throw new BizException("该举报已处理");
        }
        boolean valid = request.result() == HandleReportReq.Result.VALID;
        boolean moderate = Boolean.TRUE.equals(request.moderate());
        if (!valid && moderate) {
            throw new BizException("驳回举报时不能执行治理操作");
        }
        String action = moderate ? moderate(adminId, report) : null;
        String comment = request.comment().trim();
        report.setStatus("HANDLED");
        report.setResult(request.result().name());
        report.setHandlerId(adminId);
        report.setHandledAt(LocalDateTime.now());
        report.setHandleComment(comment);
        String resultLabel = valid ? "成立" : "驳回";
        audit.record(adminId, "HANDLE_REPORT", "REPORT", reportId,
                report.getTargetType() + " #" + report.getTargetId() + " · " + resultLabel
                        + (action == null ? "" : " · " + action) + " · " + comment);
        notifications.push(report.getReporterId(), "REPORT_HANDLED",
                "举报 #" + reportId + " 已处理（" + resultLabel + "）。处理人：管理员 #" + adminId
                        + "。处理意见：" + comment, reportId);
    }

    private String moderate(Long adminId, Report report) {
        Long targetId = report.getTargetId();
        return switch (report.getTargetType()) {
            case "POST" -> {
                removePost(adminId, targetId);
                yield "DELETE_POST";
            }
            case "TRADE_POST" -> {
                closeTrade(adminId, targetId);
                yield "CLOSE_TRADE_POST";
            }
            case "USER" -> {
                changeStudentStatus(adminId, targetId, true);
                yield "BAN_USER";
            }
            default -> throw new BizException("举报对象类型不合法");
        };
    }
}
