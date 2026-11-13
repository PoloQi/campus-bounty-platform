package com.campus.bounty.admin;

import com.campus.bounty.common.BizException;
import com.campus.bounty.community.Post;
import com.campus.bounty.community.PostRepository;
import com.campus.bounty.trade.PostStatus;
import com.campus.bounty.trade.TradePost;
import com.campus.bounty.trade.TradePostRepository;
import com.campus.bounty.user.User;
import com.campus.bounty.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final TradePostRepository tradePostRepository;
    private final ReportRepository reportRepository;

    public void requireAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException("用户不存在"));
        if (!"ADMIN".equals(user.getRole())) {
            throw new BizException(40300, "需要管理员权限");
        }
    }

    @Transactional
    public void deletePost(Long adminId, Long postId) {
        requireAdmin(adminId);
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BizException("帖子不存在"));
        post.setStatus("DELETED");
        postRepository.save(post);
    }

    @Transactional
    public void closeTradePost(Long adminId, Long postId) {
        requireAdmin(adminId);
        TradePost post = tradePostRepository.findById(postId)
                .orElseThrow(() -> new BizException("交易帖不存在"));
        post.setStatus(PostStatus.CLOSED);
        tradePostRepository.save(post);
    }

    @Transactional
    public void banUser(Long adminId, Long userId) {
        requireAdmin(adminId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException("用户不存在"));
        user.setStatus("BANNED");
        userRepository.save(user);
    }

    @Transactional
    public void report(Long reporterId, String targetType, Long targetId, String reason) {
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
}
