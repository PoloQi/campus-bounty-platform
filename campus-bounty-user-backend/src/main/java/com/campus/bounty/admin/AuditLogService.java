package com.campus.bounty.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端审计日志服务。由各管理服务显式调用（设计明确不用 AOP，便于讲清调用点）。
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {
    private static final int DETAIL_MAX = 500;

    private final AdminLogRepository adminLogRepository;

    /**
     * 不加 @Transactional：依赖调用方事务，业务回滚时日志一并回滚，
     * 不留下「动作没做成却留了痕」的脏记录（与 NotificationService.push 的处理一致）。
     * 不调 requireAdmin：SecurityConfig 的 hasRole 与调用方 AdminService.requireAdmin 已鉴权，
     * 此处再查一次 t_user 只是浪费。
     */
    public void record(Long adminId, String action, String targetType, Long targetId, String detail) {
        AdminLog log = new AdminLog();
        log.setAdminId(adminId);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetail(truncate(detail));
        adminLogRepository.save(log);
    }

    public List<AdminLog> query(Long adminId, String action, LocalDateTime from, LocalDateTime to) {
        return adminLogRepository.search(adminId, action, from, to);
    }

    /** 审计是旁路留痕，超长只截断，不能因为 detail 过长让业务动作失败。 */
    private String truncate(String detail) {
        if (detail == null) {
            return null;
        }
        return detail.length() <= DETAIL_MAX ? detail : detail.substring(0, DETAIL_MAX - 3) + "...";
    }
}
