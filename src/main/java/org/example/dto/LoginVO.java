package org.example.dto;

import lombok.Data;

import java.util.List;

@Data
public class LoginVO {
    private String token;          // Sa-Token
    private Long userId;           // 用户ID
    private String username;       // 账号
    private String realName;       // 真实姓名
    private String roleKey;        // 角色标识
    private String roleName;       // 角色名称
    private List<String> permissions; // 权限标识集合
}
