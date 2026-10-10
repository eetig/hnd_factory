package org.example.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.exception.SaTokenException;
import org.example.dto.ExcelResult;
import org.example.dto.Result;
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

    // 其它 Sa-Token 异常兜底
    @ExceptionHandler(SaTokenException.class)
    public ResponseEntity<ExcelResult<Void>> handleSaToken(SaTokenException e) {
        return build(HttpStatus.FORBIDDEN, e.getMessage());
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
