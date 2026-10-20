package org.example.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 自助注册的入参（变更-129）。
 *
 * <p>⚠️ **故意没有 role 字段**：角色一律由服务端固定成 {@code member}（只读）。
 * 让客户端传角色是越权漏洞的经典来源，这里从数据形状上就断掉。
 */
@Data
public class RegisterDTO {

    @NotBlank(message = "账号不能为空")
    @Size(min = 4, max = 32, message = "账号长度 4-32")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度 6-64")
    private String password;

    @Size(max = 32, message = "姓名最长 32")
    private String realName;
}