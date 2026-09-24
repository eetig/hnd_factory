package org.example.controller;

import org.example.dto.ExcelResult;
import org.example.dto.LoginDTO;
import org.example.dto.LoginVO;
import org.example.dto.UserInfoVO;
import org.example.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthController {

    @Autowired
    private AuthService authService;

    // 账号密码登录
    @PostMapping("/login")
    public LoginVO login(@RequestBody LoginDTO dto) {
        return authService.login(dto);
    }

    // 退出登录
    @PostMapping("/logout")
    public ExcelResult<Void> logout() {
        authService.logout();
        ExcelResult<Void> result = new ExcelResult<>();
        result.setSuccess(true);
        result.setMsg("退出成功");
        return result;
    }

    // 获取当前登录用户信息 + 权限集合（需登录；未登录返回 401）
    @GetMapping("/user/info")
    public UserInfoVO userInfo() {
        return authService.getCurrentUserInfo();
    }
}
