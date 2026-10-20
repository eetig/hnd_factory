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
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class AuthController {

    @Autowired
    private AuthService authService;

    // 账号密码登录
    /**
     * 自助注册（变更-129）：不强制任何人注册，但愿意留名的人可以自己开号。
     * 角色由服务端固定成 member（只读）；注册成功即登录，返回与 /api/login 同形状的 token。
     */
    @PostMapping("/register")
    public LoginVO register(@Valid @RequestBody org.example.dto.RegisterDTO dto) {
        return authService.register(dto);
    }

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
