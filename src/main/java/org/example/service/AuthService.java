package org.example.service;

import org.example.dto.LoginDTO;
import org.example.dto.LoginVO;

public interface AuthService {
    // 账号密码登录，成功返回 token + 用户 + 角色 + 权限
    LoginVO login(LoginDTO dto);

    // 退出登录
    void logout();
}
