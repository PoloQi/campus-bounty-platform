package com.campus.bounty.admin;

/**
 * 管理端审计动作常量。集中定义，禁止在 Service 里随手拼字符串。
 * 用常量类而非 enum：实体字段与数据库列都是 VARCHAR，与 Report.status、User.role 的既有风格一致。
 */
public final class AuditAction {
    public static final String BAN_USER = "BAN_USER";
    public static final String UNBAN_USER = "UNBAN_USER";
    public static final String DELETE_POST = "DELETE_POST";
    public static final String CLOSE_TRADE_POST = "CLOSE_TRADE_POST";
    public static final String HANDLE_REPORT = "HANDLE_REPORT";
    public static final String APPEAL_RELEASE = "APPEAL_RELEASE";
    public static final String APPEAL_REFUND = "APPEAL_REFUND";

    private AuditAction() {
    }
}
