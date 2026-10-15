package org.example.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.dev33.satoken.exception.NotWebContextException;
import cn.dev33.satoken.exception.SaTokenContextException;
import cn.dev33.satoken.exception.SaTokenException;
import org.example.dto.ExcelResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Sa-Token 异常分流的回归防线（2026-10-15 code-review 的 Critical 1）。
 *
 * <p>要钉住的是一个**故障类**，不是一个实例：把"框架上下文未初始化"这类服务端故障
 * 渲染成 403「无权限」，会让下一个人（以及当时的我们）朝着鉴权方向排查一整轮。
 * 所以这里断言状态码是 500、并且响应体**不含**框架原始消息。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("上下文类异常 → 500（服务端故障），不再伪装成 403")
    void contextExceptionsAreServerErrors() {
        ResponseEntity<ExcelResult<Void>> ctx =
                handler.handleSaToken(new SaTokenContextException("SaTokenContext 上下文尚未初始化"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ctx.getStatusCode());
        assertFalse(String.valueOf(ctx.getBody().getMsg()).contains("尚未初始化"),
                "内部消息不该回给调用方：" + ctx.getBody().getMsg());

        ResponseEntity<ExcelResult<Void>> web =
                handler.handleSaToken(new NotWebContextException("非 Web 上下文"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, web.getStatusCode());
    }

    @Test
    @DisplayName("其余 Sa-Token 异常 → 403，且文案固定（不回显 e.getMessage()）")
    void otherSaTokenExceptionsAreForbiddenWithoutEcho() {
        ResponseEntity<ExcelResult<Void>> res =
                handler.handleSaToken(new SaTokenException("内部：redis 连接池已满，端点 10.0.0.7:6379"));
        assertEquals(HttpStatus.FORBIDDEN, res.getStatusCode());
        assertTrue(String.valueOf(res.getBody().getMsg()).contains("无权限"),
                "应给统一文案：" + res.getBody().getMsg());
        assertFalse(String.valueOf(res.getBody().getMsg()).contains("redis"),
                "不该把内部细节回给调用方：" + res.getBody().getMsg());
    }
}
