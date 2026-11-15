package com.campus.bounty.admin;

import com.campus.bounty.admin.dto.HandleReportReq;
import com.campus.bounty.common.BizException;
import com.campus.bounty.community.Post;
import com.campus.bounty.community.PostRepository;
import com.campus.bounty.community.PostService;
import com.campus.bounty.community.dto.CreatePostReq;
import com.campus.bounty.config.JwtUtil;
import com.campus.bounty.notification.Notification;
import com.campus.bounty.notification.NotificationService;
import com.campus.bounty.trade.PostStatus;
import com.campus.bounty.trade.TradePost;
import com.campus.bounty.trade.TradePostRepository;
import com.campus.bounty.trade.TradePostService;
import com.campus.bounty.trade.dto.ServicePublishReq;
import com.campus.bounty.user.User;
import com.campus.bounty.user.UserRepository;
import com.campus.bounty.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B4 举报处置闭环验收。
 * <p>
 * 重点断言三件事：处置动作真的改了目标对象状态；一次举报只产生**一条** HANDLE_REPORT 审计
 * （不复用 AdminService 的写方法，否则会多出 DELETE_POST/CLOSE_TRADE_POST/BAN_USER 记录）；
 * 举报人会收到 REPORT_HANDLED 通知。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@SuppressWarnings("unchecked")
class AdminReportFlowTest {
    @LocalServerPort
    int port;
    @Autowired
    TestRestTemplate rest;
    @Autowired
    AdminService adminService;
    @Autowired
    AdminReportService adminReportService;
    @Autowired
    AuditLogService auditLogService;
    @Autowired
    NotificationService notificationService;
    @Autowired
    ReportRepository reportRepository;
    @Autowired
    UserService userService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    PostService postService;
    @Autowired
    PostRepository postRepository;
    @Autowired
    TradePostService tradePostService;
    @Autowired
    TradePostRepository tradePostRepository;
    @Autowired
    JwtUtil jwtUtil;

    @Test
    void delete_post_action_hides_post_and_leaves_single_audit() {
        User author = register("rpt_del_author");
        User reporter = register("rpt_del_reporter");
        Post post = createPost(author, "售卖违禁物品");

        Long reportId = report(reporter, "POST", post.getId(), "违规广告");
        Report handled = handle(reportId, "DELETE_POST", "核实违规，已删除");

        assertEquals("HANDLED", handled.getStatus());
        assertEquals("VALID", handled.getResult());
        assertEquals(admin().getId(), handled.getHandlerId());
        assertNotNull(handled.getHandledAt());
        assertEquals("核实违规，已删除", handled.getHandleComment());
        assertEquals("DELETED", postRepository.findById(post.getId()).orElseThrow().getStatus());

        List<AdminLog> audits = auditLogs(reportId);
        assertEquals(1, audits.size(), "一次处置只应留一条 HANDLE_REPORT 审计");
        assertEquals(AuditAction.HANDLE_REPORT, audits.get(0).getAction());
        assertEquals("REPORT", audits.get(0).getTargetType());

        // 关键：没有因为复用 AdminService.deletePost 而多出一条 DELETE_POST 审计
        assertTrue(auditLogService.query(admin().getId(), AuditAction.DELETE_POST, null, null).stream()
                        .noneMatch(log -> post.getId().equals(log.getTargetId())),
                "举报处置不应产生 DELETE_POST 审计");

        Notification notification = notificationOf(reporter, reportId);
        assertEquals("REPORT_HANDLED", notification.getType());
        assertTrue(notification.getContent().contains("已成立"), notification.getContent());
    }

    @Test
    void close_trade_post_action_closes_service() {
        User author = register("rpt_close_author");
        User reporter = register("rpt_close_reporter");
        TradePost service = publishService(author, "代写作业服务");

        Long reportId = report(reporter, "TRADE_POST", service.getId(), "违规服务");
        Report handled = handle(reportId, "CLOSE_TRADE_POST", null);

        assertEquals("VALID", handled.getResult());
        assertEquals(PostStatus.CLOSED,
                tradePostRepository.findById(service.getId()).orElseThrow().getStatus());
        assertEquals(1, auditLogs(reportId).size());
    }

    @Test
    void ban_user_action_bans_content_author() {
        User author = register("rpt_ban_author");
        User reporter = register("rpt_ban_reporter");
        Post post = createPost(author, "辱骂他人");

        Long reportId = report(reporter, "POST", post.getId(), "人身攻击");
        handle(reportId, "BAN_USER", null);

        assertEquals("BANNED", userRepository.findById(author.getId()).orElseThrow().getStatus(),
                "BAN_USER 应作用于被举报内容的作者");
        assertThrows(BizException.class, () -> userService.login(author.getUsername(), "123456"),
                "封禁后应无法登录");

        // 举报人自己不能被误伤
        assertEquals("ACTIVE", userRepository.findById(reporter.getId()).orElseThrow().getStatus());
    }

    @Test
    void reject_action_keeps_target_visible() {
        User author = register("rpt_reject_author");
        User reporter = register("rpt_reject_reporter");
        Post post = createPost(author, "正常分享帖");

        Long reportId = report(reporter, "POST", post.getId(), "我不喜欢");
        Report handled = handle(reportId, "REJECT", "内容不违规");

        assertEquals("REJECTED", handled.getResult());
        assertEquals("VISIBLE", postRepository.findById(post.getId()).orElseThrow().getStatus());
        assertEquals("ACTIVE", userRepository.findById(author.getId()).orElseThrow().getStatus());
        assertFalse(notificationOf(reporter, reportId).getContent().contains("已成立"));
        assertTrue(notificationOf(reporter, reportId).getContent().contains("驳回"));
    }

    @Test
    void action_must_match_target_type() {
        User author = register("rpt_mismatch_author");
        User reporter = register("rpt_mismatch_reporter");
        Post post = createPost(author, "帖子");

        Long reportId = report(reporter, "POST", post.getId(), "试试类型不匹配");

        BizException error = assertThrows(BizException.class,
                () -> handle(reportId, "CLOSE_TRADE_POST", null));
        assertTrue(error.getMessage().contains("不支持"), error.getMessage());
        assertEquals("PENDING", reportRepository.findById(reportId).orElseThrow().getStatus(),
                "校验失败不应改动举报状态");
        assertEquals("VISIBLE", postRepository.findById(post.getId()).orElseThrow().getStatus());
    }

    @Test
    void handled_report_cannot_be_handled_twice() {
        User author = register("rpt_twice_author");
        User reporter = register("rpt_twice_reporter");
        Post post = createPost(author, "重复处置");

        Long reportId = report(reporter, "POST", post.getId(), "重复处置测试");
        handle(reportId, "DELETE_POST", null);

        BizException error = assertThrows(BizException.class,
                () -> handle(reportId, "DELETE_POST", null));
        assertTrue(error.getMessage().contains("已处理"), error.getMessage());
    }

    @Test
    void admin_filters_reports_by_status_and_target_type() {
        User author = register("rpt_filter_author");
        User reporter = register("rpt_filter_reporter");
        Post post = createPost(author, "筛选测试帖");
        TradePost service = publishService(author, "筛选测试服务");

        Long handledId = report(reporter, "POST", post.getId(), "筛选-已处理");
        handle(handledId, "REJECT", null);
        Long pendingId = report(reporter, "TRADE_POST", service.getId(), "筛选-待处理");

        List<Report> handled = adminReportService.list(admin().getId(), "HANDLED", null);
        assertTrue(ids(handled).contains(handledId));
        assertFalse(ids(handled).contains(pendingId));

        List<Report> pending = adminReportService.list(admin().getId(), "PENDING", null);
        assertTrue(ids(pending).contains(pendingId));
        assertFalse(ids(pending).contains(handledId));

        List<Report> tradeReports = adminReportService.list(admin().getId(), null, "TRADE_POST");
        assertTrue(tradeReports.stream().allMatch(row -> "TRADE_POST".equals(row.getTargetType())));
        assertTrue(ids(tradeReports).contains(pendingId));

        List<Report> all = adminReportService.list(admin().getId(), null, null);
        assertTrue(ids(all).containsAll(List.of(handledId, pendingId)));
    }

    @Test
    void student_forbidden_and_unknown_report_returns_biz_error() {
        User student = register("rpt_sec_student");

        ResponseEntity<Map> listDenied = rest.exchange(url("/api/admin/reports"),
                HttpMethod.GET, new HttpEntity<>(authHeaders(student)), Map.class);
        assertEquals(HttpStatus.FORBIDDEN, listDenied.getStatusCode());

        ResponseEntity<Map> handleDenied = rest.postForEntity(url("/api/admin/reports/1/handle"),
                new HttpEntity<>(Map.of("action", "REJECT"), authHeaders(student)), Map.class);
        assertEquals(HttpStatus.FORBIDDEN, handleDenied.getStatusCode());

        ResponseEntity<Map> notFound = rest.postForEntity(url("/api/admin/reports/99999999/handle"),
                new HttpEntity<>(Map.of("action", "REJECT"), authHeaders(admin())), Map.class);
        assertEquals(HttpStatus.OK, notFound.getStatusCode());
        assertEquals(40000, ((Number) notFound.getBody().get("code")).intValue());

        ResponseEntity<Map> badAction = rest.postForEntity(url("/api/admin/reports/99999999/handle"),
                new HttpEntity<>(Map.of("action", "DROP_DATABASE"), authHeaders(admin())), Map.class);
        assertEquals(40000, ((Number) badAction.getBody().get("code")).intValue(),
                "非法 action 应被 @Pattern 拦下");
    }

    private Report handle(Long reportId, String action, String comment) {
        HandleReportReq request = new HandleReportReq();
        request.setAction(action);
        request.setComment(comment);
        return adminReportService.handle(admin().getId(), reportId, request);
    }

    private Long report(User reporter, String targetType, Long targetId, String reason) {
        adminService.report(reporter.getId(), targetType, targetId, reason);
        return reportRepository.searchForAdmin("PENDING", null).stream()
                .filter(row -> row.getReporterId().equals(reporter.getId())
                        && row.getTargetId().equals(targetId)
                        && row.getTargetType().equals(targetType))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    private List<AdminLog> auditLogs(Long reportId) {
        return auditLogService.query(admin().getId(), AuditAction.HANDLE_REPORT, null, null).stream()
                .filter(log -> reportId.equals(log.getTargetId()))
                .toList();
    }

    private Notification notificationOf(User reporter, Long reportId) {
        return notificationService.list(reporter.getId()).stream()
                .filter(row -> "REPORT_HANDLED".equals(row.getType()) && reportId.equals(row.getRefId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("举报人未收到 REPORT_HANDLED 通知"));
    }

    private List<Long> ids(List<Report> reports) {
        return reports.stream().map(Report::getId).toList();
    }

    private User admin() {
        return userRepository.findByUsername("admin").orElseThrow();
    }

    private Post createPost(User author, String content) {
        CreatePostReq request = new CreatePostReq();
        request.setContent(content);
        return postService.create(author.getId(), request);
    }

    private TradePost publishService(User author, String title) {
        ServicePublishReq request = new ServicePublishReq();
        request.setTitle(title);
        request.setDescription("举报处置测试用服务帖");
        request.setPrice(new BigDecimal("9.90"));
        request.setCategory("学习");
        return tradePostService.publishService(author.getId(), request);
    }

    private User register(String username) {
        return userService.register(username, "123456", "123456", "举报测试用户");
    }

    private HttpHeaders authHeaders(User user) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtUtil.generate(user.getId(), user.getUsername()));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
