package org.example.service;

import org.example.dto.LoginDTO;
import org.example.dto.LoginVO;
import org.example.dto.UserInfoVO;

public interface AuthService {
    // 账号密码登录，成功返回 token + 用户 + 角色 + 权限
    LoginVO login(LoginDTO dto);

    /**
     * 自助注册（变更-129）：角色由服务端固定成 member（只读），注册成功即登录。
     * 关闭注册开关时抛 BusinessException（提示联系管理员开通）。
     */
    LoginVO register(org.example.dto.RegisterDTO dto);

    // 退出登录
    void logout();

    // 获取当前登录用户信息 + 权限集合（未登录时抛 NotLoginException -> 401）
    UserInfoVO getCurrentUserInfo();
}
