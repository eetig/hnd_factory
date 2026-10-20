package org.example.exception;

/**
 * 「同一天同一容器已有一条记录」——需要用户再确认一次的**可继续**冲突（2026-10-20，变更-127）。
 *
 * <p>与 {@link BusinessException} 的区别就是"可继续"：业务异常是拒绝，
 * 而这类冲突只要带上 {@code confirmDuplicate=true} 重提就能存进去，所以前端必须能把它
 * 和普通失败区分开（见 GlobalExceptionHandler：回业务码 <b>409</b>，HTTP 仍是 200）。
 *
 * <p>为什么不用 {@link org.example.dto.Result#fail}：那样前端只能拿到一句文案，
 * 没法自动弹出"仍要保存 / 取消"——而这段文案本身就是"要不要继续"的提问。
 */
public class TankLevelDuplicateException extends RuntimeException {
    public TankLevelDuplicateException(String message) {
        super(message);
    }
}
