package org.example.constant;

/**
 * 审计事件的类型常量（变更-129）。用常量而不是散落的字符串字面量 ——
 * 查询页、统计、单测都靠这份清单，写错一个字母查不出来。
 */
public final class AuditEventType {

    /** 自助注册 */
    public static final String REGISTER = "register";
    /** 登录成功 */
    public static final String LOGIN = "login";
    /** 退出登录 */
    public static final String LOGOUT = "logout";
    /** 写操作被拒（非 admin 或未登录） */
    public static final String WRITE_DENIED = "write_denied";
    /** 写操作成功（admin） */
    public static final String WRITE_OK = "write_ok";
    /** 点了锁定功能（游客/普通用户点了需要注册或需要管理员的功能） */
    public static final String LOCKED_CLICKED = "locked_clicked";

    private AuditEventType() {
    }
}