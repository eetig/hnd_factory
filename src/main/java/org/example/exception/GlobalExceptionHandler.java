package org.example.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.exception.NotWebContextException;
import cn.dev33.satoken.exception.SaTokenContextException;
import cn.dev33.satoken.exception.SaTokenException;
import org.example.dto.ExcelResult;
import org.example.dto.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器：
 * - Sa-Token 鉴权异常返回 ExcelResult（前端识别 401/403 跳转登录页）；
 * - 业务异常返回通用 Result（{code, msg, data}）。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 未登录 / 登录失效（前端识别 401 后跳转登录页）
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<ExcelResult<Void>> handleNotLogin(NotLoginException e) {
        return build(HttpStatus.UNAUTHORIZED, "未登录或登录已过期");
    }

    // 无权限
    @ExceptionHandler(NotPermissionException.class)
    public ResponseEntity<ExcelResult<Void>> handleNotPermission(NotPermissionException e) {
        return build(HttpStatus.FORBIDDEN, "无权限访问：" + e.getPermission());
    }

    // 无角色：写操作（增删改）只允许 admin，见 SaTokenConfigure 的集中闸门
    @ExceptionHandler(NotRoleException.class)
    public ResponseEntity<ExcelResult<Void>> handleNotRole(NotRoleException e) {
        return build(HttpStatus.FORBIDDEN, "无权限访问：该操作仅管理员可用");
    }

    /**
     * 其它 Sa-Token 异常兜底 —— **必须收窄**（变更-065）。
     *
     * <p>原先这条把**任意** {@link SaTokenException} 映射成 403 + {@code e.getMessage()}，
     * 而 {@link SaTokenContextException}（上下文未初始化）与 {@link NotWebContextException}
     * （非 Web 上下文）描述的是**服务端故障**，不是"调用者无权限"。2026-10-15 的事故正是如此：
     * 一条 DB 约束错误在 ERROR 派发里触发了上下文异常，被这条兜底渲染成
     * 403「SaTokenContext 上下文尚未初始化」，真实原因彻底看不见 ——
     * 当时只补了 DataIntegrityViolationException 这个**症状出口**，故障类仍在原地。
     *
     * <p>现在按类型分流：上下文类 → 500（如实报服务端故障）；其余 → 403 且文案固定，
     * 不再把框架原始消息回给调用方。
     *
     * <p>注意：Sa-Token 1.46 里**不存在** {@code SaTokenConfigException}（评审曾建议一并列入），
     * 所以这里只列确实存在的两个上下文类。
     */
    @ExceptionHandler(SaTokenException.class)
    public ResponseEntity<ExcelResult<Void>> handleSaToken(SaTokenException e) {
        if (e instanceof SaTokenContextException || e instanceof NotWebContextException) {
            log.error("Sa-Token 上下文异常（服务端故障，非鉴权失败）", e);
            return build(HttpStatus.INTERNAL_SERVER_ERROR, "服务内部错误（鉴权上下文未初始化）");
        }
        log.warn("Sa-Token 异常：{}", e.getMessage());
        return build(HttpStatus.FORBIDDEN, "无权限访问");
    }

    /**
     * 数据库约束 / 完整性错误（变更-061）。
     *
     * <p>为什么必须单独映射：这类异常原先没有处理器，会冒泡出 DispatcherServlet 触发 ERROR
     * 派发；而那次派发里 Sa-Token 的上下文并没有初始化，于是又抛
     * {@link SaTokenException}，被上面那条兜底处理成
     * <b>403 +「SaTokenContext 上下文尚未初始化」</b> —— 真正的原因（哪一列不能为空）
     * 被彻底盖掉，排查时完全看不出方向（2026-10-15 实测于「设备数据维护」的停用按钮）。
     *
     * <p>这里只说「与约束冲突」，不把 SQL / 列名细节吐给前端。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ExcelResult<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "数据完整性校验失败：写入的字段与数据库约束冲突（例如必填列为空）");
    }

    /**
     * 单据并发冲突（变更-047）：回业务码 <b>409</b>（HTTP 仍是 200），与普通业务失败区分。
     *
     * <p>前端据此自动重查单据并提示「重新载入」；普通 500 文案只做展示。
     * 之所以要这个兜底映射：{@code OcrConfirmController} 自己也 catch 了它（因为它那里的
     * 大 catch 会吞异常），但任何**别的**入口（如新加的 detail 接口内部复用校验）抛出的
     * 冲突都得有统一出口，不能落成 500。
     *
     * <p>Note：Spring 选最具体的处理器，本方法优先于下面的 {@link BusinessException} 兜底。
     */
    @ExceptionHandler(DocumentConflictException.class)
    public Result<Void> handleDocumentConflict(DocumentConflictException e) {
        return Result.error(409, e.getMessage());
    }

    // 业务异常（登录失败、图片上传失败等），返回通用 Result
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        return Result.error(500, e.getMessage());
    }

    /**
     * 入参校验失败（{@code @Valid} 挂在查询 DTO / 请求体上时）。
     *
     * <p>目前只有一处会走到：列表接口的 {@link org.example.dto.PageQueryDTO} 要求
     * {@code pageNum} / {@code pageSize} 必传（见该类的注释 —— 老版 App 不带分页参数，
     * 给默认值会让它安静地只拿到第 1 页，比 400 难排查得多）。
     *
     * <p>校验失败的是**协议**而不是业务，所以回 HTTP 400，并且用与其它错误一致的
     * ExcelResult 形状（前端统一读 {@code msg}），而不是 Spring 默认那坨 Whitelabel 错误页。
     *
     * <p>注意 {@code MethodArgumentNotValidException} 是 {@link BindException} 的子类，
     * 所以这一个处理器同时覆盖「查询参数绑定」（@ModelAttribute）与
     * 「请求体校验」（@Valid @RequestBody）两种情况 —— 后者本工程现在没用，将来加了也一并管住。
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ExcelResult<Void>> handleBind(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null
                        ? error.getField() + " 参数不合法"
                        : error.getDefaultMessage())
                .orElse("请求参数不合法");
        return build(HttpStatus.BAD_REQUEST, msg);
    }

    private ResponseEntity<ExcelResult<Void>> build(HttpStatus status, String msg) {
        ExcelResult<Void> result = new ExcelResult<>();
        result.setSuccess(false);
        result.setMsg(msg);
        return ResponseEntity.status(status).body(result);
    }
}
