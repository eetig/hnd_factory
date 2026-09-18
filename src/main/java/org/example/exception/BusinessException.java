package org.example.exception;

/**
 * 业务异常，登录失败等场景抛出，由 GlobalExceptionHandler 统一处理。
 */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
