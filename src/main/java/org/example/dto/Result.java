package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用返回体：{code, msg, data, success}。
 * success 布尔字段便于前端直接判断成败。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {
    private int code;
    private String msg;
    private T data;
    private boolean success;

    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data, true);
    }

    public static <T> Result<T> success(String msg, T data) {
        return new Result<>(200, msg, data, true);
    }

    public static <T> Result<T> error(int code, String msg) {
        return new Result<>(code, msg, null, false);
    }

    public static <T> Result<T> fail(String msg) {
        return new Result<>(500, msg, null, false);
    }
}
