package org.example.dto;

import lombok.Data;

import java.util.List;

/**
 * 当前登录用户信息（不含 token）。用于前端刷新页面后恢复角色/权限状态。
 */
@Data
public class UserInfoVO {
    private Long userId;              // 用户ID
    private String username;          // 账号
    private String realName;          // 真实姓名
    private String roleKey;           // 角色标识 admin/team_leader/operator/guest
    private String roleName;          // 角色名称
    private List<String> permissions; // 权限标识集合
}
