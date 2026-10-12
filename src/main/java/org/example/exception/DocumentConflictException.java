package org.example.exception;

/**
 * document 模式落库的并发冲突（变更-047）：行数已被他人改动、行 id 已不存在等。
 *
 * <p>契约上这类失败要回业务码 <b>409</b>（HTTP 仍是 200），前端据此**自动重查单据**
 * 并提示用户重新载入 —— 与普通校验失败（500 文案）区分开。
 * 映射见 {@code GlobalExceptionHandler#handleDocumentConflict}。
 */
public class DocumentConflictException extends BusinessException {

    public DocumentConflictException(String message) {
        super(message);
    }
}
