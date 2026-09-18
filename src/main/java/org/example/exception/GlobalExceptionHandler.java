package org.example.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.SaTokenException;
import org.example.dto.ExcelResult;
import org.example.dto.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    private ResponseEntity<ExcelResult<Void>> build(HttpStatus status, String msg) {
        ExcelResult<Void> result = new ExcelResult<>();
        result.setSuccess(false);
        result.setMsg(msg);
        return ResponseEntity.status(status).body(result);
    }
}
